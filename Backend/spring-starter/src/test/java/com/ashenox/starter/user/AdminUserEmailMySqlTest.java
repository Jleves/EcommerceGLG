package com.ashenox.starter.user;

import com.ashenox.starter.auth.challenge.model.*;
import com.ashenox.starter.auth.challenge.service.ChallengeException;
import com.ashenox.starter.auth.model.LoginRequest;
import com.ashenox.starter.auth.session.service.IssuedSession;
import com.ashenox.starter.security.error.*;
import com.ashenox.starter.user.dto.UpdateUserEmailRequest;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.service.AdminUserConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import java.sql.DriverManager;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminUserEmailMySqlTest extends AdminUserEmailTestSupport {
    @Container @ServiceConnection static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4");
    @org.springframework.beans.factory.annotation.Autowired com.ashenox.starter.user.service.AdminMutationGuard guard;

    @Test void resetCreatedAfterAdministrativeSnapshotIsStillInvalidated() throws Exception {
        var snapshot=new CountDownLatch(1);var waiting=new AtomicReference<Future<?>>();
        var tokenId=new AtomicReference<Long>();
        try(var pool=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                guard.lock(actor,target.getId());
                waiting.set(pool.submit(()->new TransactionTemplate(transactions).executeWithoutResult(worker->{
                    assertThat(resets.findByUser(target)).isEmpty();
                    snapshot.countDown();
                    service.updateEmail(target.getId(),new UpdateUserEmailRequest("snapshot."+target.getId()+"@example.com"),actor);
                })));
                await(snapshot);awaitWait("admin_mutation_lock");
                tokenId.set(reset().getId());
            });
            waiting.get().get(20,TimeUnit.SECONDS);
        }
        assertThat(resets.findById(tokenId.get()).orElseThrow().getUsedAt()).isNotNull();
        assertThatThrownBy(()->resetService.validateToken("reset-"+target.getId())).isInstanceOf(InvalidTokenException.class);
    }

    @Test void twoAccountsCompeteForSameEmailOnlyOneCommitsEvenWithOldSnapshot() throws Exception {
        var other=user(Role.USER);
        String email="race."+target.getId()+"@example.com";
        var waiting=new AtomicReference<Future<?>>();
        try(var pool=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                service.updateEmail(target.getId(),new UpdateUserEmailRequest(email),actor);
                waiting.set(pool.submit(()->assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(worker->{
                    users.findAll(); // snapshot predates the winning commit
                    service.updateEmail(other.getId(),new UpdateUserEmailRequest(email),actor);
                })).isInstanceOf(AdminUserConflictException.class)
                        .extracting("code").isEqualTo(com.ashenox.starter.shared.error.ApiErrorCode.ADMIN_EMAIL_IN_USE)));
                awaitWait("admin_mutation_lock");
            });
            waiting.get().get(20,TimeUnit.SECONDS);
        }
        assertThat(current().getEmail()).isEqualTo(email);
        assertThat(users.findById(other.getId()).orElseThrow().getEmail()).isEqualTo(other.getEmail());
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void emailVersusEnableRespectsWhicheverCommitsFirst(boolean emailFirst) throws Exception {
        var pending=mfa.start(target.getId(),origin.session().getId(),ChallengePurpose.ENABLE,"secure-password");
        delivery.deliverCreated(pending);
        var waiting=new AtomicReference<Future<?>>();
        Runnable enable=()->mfa.confirm(target.getId(),origin.session().getId(),ChallengePurpose.ENABLE,pending.cookieValue(),pending.code());
        Runnable change=()->service.updateEmail(target.getId(),new UpdateUserEmailRequest("enable."+target.getId()+"@example.com"),actor);
        try(var pool=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                if(emailFirst)change.run(); else enable.run();
                waiting.set(pool.submit(()->{
                    if(emailFirst)assertThatThrownBy(enable::run).isInstanceOf(InvalidAuthSessionException.class);
                    else assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(worker->{
                        users.findById(target.getId()); // cache/snapshot still sees MFA disabled before the competing commit
                        change.run();
                    })).isInstanceOf(AdminUserConflictException.class);
                }));
                awaitWait("users");
            });
            waiting.get().get(20,TimeUnit.SECONDS);
        }
        assertThat(current().isEmailMfaEnabled()).isEqualTo(!emailFirst);
        assertThat(current().getEmail()).isEqualTo(emailFirst?"enable."+target.getId()+"@example.com":target.getEmail());
        assertThat(current().getSecurityVersion()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void emailVersusLoginCannotLeaveOldEmailSessionUsable(boolean emailFirst) throws Exception {
        var request=new LoginRequest();request.setEmail(target.getEmail());request.setPassword("secure-password");
        var issued=new AtomicReference<String>();var waiting=new AtomicReference<Future<?>>();
        try(var pool=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                if(emailFirst)service.updateEmail(target.getId(),new UpdateUserEmailRequest("login."+target.getId()+"@example.com"),actor);
                else issued.set(login.login(request).authentication().sessionId());
                waiting.set(pool.submit(()->{
                    if(emailFirst)assertThatThrownBy(()->login.login(request)).isInstanceOf(InvalidCredentialsException.class);
                    else service.updateEmail(target.getId(),new UpdateUserEmailRequest("login."+target.getId()+"@example.com"),actor);
                }));
                awaitWait("users");
            });
            waiting.get().get(20,TimeUnit.SECONDS);
        }
        if(issued.get()!=null)assertThat(sessionRepository.findById(issued.get()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(current().getEmail()).isEqualTo("login."+target.getId()+"@example.com");
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void emailVersusRefreshCannotResurrectRevokedSession(boolean emailFirst) throws Exception {
        var waiting=new AtomicReference<Future<?>>();var rotated=new AtomicReference<IssuedSession>();
        try(var pool=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                if(emailFirst)service.updateEmail(target.getId(),new UpdateUserEmailRequest("refresh."+target.getId()+"@example.com"),actor);
                else rotated.set(sessions.rotate(origin.refreshToken()));
                waiting.set(pool.submit(()->{
                    if(emailFirst)assertThatThrownBy(()->sessions.rotate(origin.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
                    else service.updateEmail(target.getId(),new UpdateUserEmailRequest("refresh."+target.getId()+"@example.com"),actor);
                }));
                try { awaitWait("auth_sessions"); }
                catch (AssertionError failure) {
                    if (waiting.get().isDone()) {
                        try { waiting.get().get(); }
                        catch (Exception workerFailure) { throw new AssertionError("Refresh worker finished before lock observation", workerFailure); }
                    }
                    throw failure;
                }
            });
            waiting.get().get(20,TimeUnit.SECONDS);
        }
        assertThat(sessionRepository.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
        if(rotated.get()!=null)assertThatThrownBy(()->sessions.rotate(rotated.get().refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test void lateSmtpCannotMakeInvalidatedEnableChallengeUsable() throws Exception {
        var pending=mfa.start(target.getId(),origin.session().getId(),ChallengePurpose.ENABLE,"secure-password");
        var sending=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(invocation->{sending.countDown();await(release);return null;})
                .when(sender).sendCode(anyString(),any(),anyString(),any());
        try(var pool=Executors.newSingleThreadExecutor()) {
            var result=pool.submit(()->assertThatThrownBy(()->delivery.deliverCreated(pending)).isInstanceOf(ChallengeException.class));
            try {
                await(sending);
                service.updateEmail(target.getId(),new UpdateUserEmailRequest("smtp."+target.getId()+"@example.com"),actor);
            } finally {release.countDown();}
            result.get(20,TimeUnit.SECONDS);
        }
        var saved=challengeRepository.findById(pending.challenge().getId()).orElseThrow();
        assertThat(saved.getInvalidatedAt()).isNotNull();
        assertThat(saved.getDeliveryState()).isEqualTo(ChallengeDeliveryState.PENDING);
        assertThatThrownBy(()->challenges.verifyAndConsume(pending.cookieValue(),ChallengePurpose.ENABLE,target.getId(),origin.session().getId(),pending.code()))
                .isInstanceOf(ChallengeException.class);
        assertThat(current().isEmailMfaEnabled()).isFalse();
    }

    static void await(CountDownLatch latch) {
        try {assertThat(latch.await(15,TimeUnit.SECONDS)).isTrue();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
    static void awaitWait(String table) {
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());
            var statement=connection.prepareStatement("select count(*) from performance_schema.data_lock_waits w join performance_schema.data_locks l on l.engine_lock_id=w.requesting_engine_lock_id where l.object_schema=? and l.object_name=?")) {
            statement.setString(1,MYSQL.getDatabaseName());statement.setString(2,table);
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            do {try(var rows=statement.executeQuery()){rows.next();if(rows.getInt(1)>0)return;}Thread.sleep(20);}while(System.nanoTime()<end);
            throw new AssertionError("No MySQL lock wait on "+table);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
