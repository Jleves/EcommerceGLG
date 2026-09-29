package com.ashenox.starter.auth.challenge.event;

import com.ashenox.starter.auth.challenge.port.MfaEmailSender;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MfaSettingsEmailListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MfaSettingsEmailListener.class);
    private final MfaEmailSender sender;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettingsChanged(MfaSettingsChanged event) {
        try {
            sender.sendSettingsChanged(event.recipient(), event.enabled());
            LOGGER.info("MFA settings notification accepted userId={}", event.userId());
        } catch (RuntimeException exception) {
            LOGGER.error("MFA settings notification failed userId={}", event.userId());
        }
    }
}
