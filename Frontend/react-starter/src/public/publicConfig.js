import ofertasParaTuObra from '../assets/Ofertas para tu obra.png'
import asesorateConGlg from '../assets/Asesorate con GLG Construcciones.png'
import corralonExterior from '../assets/Corralon Exterior.webp'
import calSantaBarbaraLogo from '../assets/Marcas/Marca Carril/Cal Santa barbara.png'
import cunmalleuLogo from '../assets/Marcas/Marca Carril/Cunmalleu.png'
import durlockLogo from '../assets/Marcas/Marca Carril/Durlock.png'
import emtopLogo from '../assets/Marcas/Marca Carril/EMTOP.png'
import klaukolLogo from '../assets/Marcas/Marca Carril/Klaukol.png'
import lomaNegraLogo from '../assets/Marcas/Marca Carril/Loma_Negra.png'
import mapeiLogo from '../assets/Marcas/Marca Carril/Mapei.png'

// campaignPhotos: [{ src: fotoImportada, alt: 'Descripción de la campaña', caption?: 'Texto sobre la foto', portrait?: true }]
// brandLogos: [{ name: 'Valor exacto de la característica Marca', src: logoImportado }]
// whatsappNumber: solo dígitos, con código de país, para https://wa.me/.
export const campaignPhotos = [
  {
    src: ofertasParaTuObra,
    alt: 'GLG Construcciones: ofertas para tu obra, materiales para seguir construyendo',
  },
  {
    src: asesorateConGlg,
    alt: 'GLG Construcciones: asesorate con nosotros, consultanos por WhatsApp',
  },
  {
    src: corralonExterior,
    alt: 'Exterior de Corralón GLG en General Roca',
    caption: 'Estamos en General Roca',
    portrait: true,
  },
]
export const brandLogos = [
  { name: 'Cal Santa Bárbara', src: calSantaBarbaraLogo },
  { name: 'Cunmalleu', src: cunmalleuLogo },
  { name: 'Durlock', src: durlockLogo },
  { name: 'EMTOP', src: emtopLogo },
  { name: 'Klaukol', src: klaukolLogo },
  { name: 'Loma Negra', src: lomaNegraLogo },
  { name: 'Mapei', src: mapeiLogo },
]
export const whatsappNumber = '5492984400705'
