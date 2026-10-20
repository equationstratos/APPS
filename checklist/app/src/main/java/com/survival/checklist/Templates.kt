package com.survival.checklist

// Built-in templates. Every item comes from the official French sources listed with each
// template (government guide « Tous responsables » 2025, Géorisques, ministry of ecological
// transition, Enedis, Bison Futé, Sécurité routière, Croix-Rouge française, ministry of sports,
// FFRandonnée). Nothing is invented: wording is condensed, never extended.

data class TemplateSection(val title: String, val items: List<String>)

data class Template(
    val key: String,
    val name: String,
    val summary: String,
    val sources: List<String>,
    val sections: List<TemplateSection>
) {
    val itemCount: Int get() = sections.sumOf { it.items.size }
}

private fun section(title: String, vararg items: String) = TemplateSection(title, items.toList())

object Templates {

    val all: List<Template> = listOf(
        Template(
            key = "kit72h",
            name = "Kit d'urgence 72 h (domicile)",
            summary = "De quoi tenir 3 jours chez soi, sans eau courante ni électricité.",
            sources = listOf(
                "guide « Tous responsables » (SGDSN, 2025)",
                "info.gouv.fr, « Se préparer à une situation d'urgence »",
                "ministère de la Transition écologique et Géorisques, kit d'urgence 72 h"
            ),
            sections = listOf(
                section(
                    "Eau et nourriture",
                    "Eau potable : 6 L par personne",
                    "Pastilles de purification d'eau",
                    "Nourriture non périssable, sans cuisson (conserves…)",
                    "Ouvre-boîte",
                    "Lait et petits pots pour bébé, si besoin",
                    "Nourriture pour animaux, si besoin"
                ),
                section(
                    "Santé",
                    "Médicaments personnels",
                    "Trousse de premiers secours (pansements, compresses, alcool…)",
                    "Masques",
                    "Lunettes de vue de rechange"
                ),
                section(
                    "Éclairage et énergie",
                    "Lampe de poche et piles de rechange",
                    "Bougies",
                    "Briquet ou allumettes"
                ),
                section(
                    "Communication",
                    "Radio à piles et piles de rechange",
                    "Chargeur de téléphone ou batterie externe"
                ),
                section(
                    "Vêtements",
                    "Vêtements chauds",
                    "Couverture de survie"
                ),
                section(
                    "Documents et argent",
                    "Copies des papiers essentiels (identité, ordonnances) en pochette étanche",
                    "Argent liquide",
                    "Double des clés (maison, voiture)"
                ),
                section(
                    "Outils",
                    "Couteau multifonction"
                ),
                section(
                    "Entretien",
                    "Kit vérifié : dates de péremption et piles (2 fois par an)"
                )
            )
        ),
        Template(
            key = "evacuation",
            name = "Sac d'évacuation",
            summary = "Un sac prêt à emporter par personne, selon les 5 besoins vitaux.",
            sources = listOf(
                "Croix-Rouge française, sac d'urgence (Catakit)",
                "Géorisques, consignes en cas d'évacuation"
            ),
            sections = listOf(
                section(
                    "S'hydrater et se nourrir",
                    "Bouteilles d'eau",
                    "Aliments non périssables, sans cuisson",
                    "Couverts",
                    "Couteau multifonction"
                ),
                section(
                    "Se soigner",
                    "Trousse de premiers secours",
                    "Médicaments habituels",
                    "Kit d'hygiène"
                ),
                section(
                    "Se protéger",
                    "Vêtements chauds",
                    "Couverture de survie"
                ),
                section(
                    "Se signaler et s'informer",
                    "Sifflet",
                    "Lampe de poche et piles de rechange",
                    "Radio à piles et piles de rechange",
                    "Téléphone et batterie externe chargée"
                ),
                section(
                    "Documents et argent",
                    "Copies des documents importants en pochette étanche",
                    "Argent liquide",
                    "Double des clés",
                    "Bloc-notes et crayon"
                ),
                section(
                    "Au moment de partir",
                    "Couper le gaz, l'électricité et l'eau",
                    "Suivre les consignes des autorités",
                    "Rejoindre le point de regroupement indiqué"
                )
            )
        ),
        Template(
            key = "coupure",
            name = "Coupure d'électricité",
            summary = "Se préparer à une panne de courant et adopter les bons gestes.",
            sources = listOf(
                "Enedis, « Je n'ai plus d'électricité : les bons gestes à adopter »",
                "info.gouv.fr, kit d'urgence (coupures d'électricité, de gaz et d'eau)"
            ),
            sections = listOf(
                section(
                    "Se préparer",
                    "Lampe de poche ou lanterne et piles de rechange",
                    "Bougies, briquet ou allumettes",
                    "Radio à piles et piles de rechange",
                    "Batterie externe chargée",
                    "Eau potable : 6 L par personne",
                    "Nourriture sans cuisson",
                    "Argent liquide (distributeurs hors service)"
                ),
                section(
                    "Pendant la coupure",
                    "Vérifier lampes, prises et disjoncteur",
                    "Regarder si les voisins sont aussi privés de courant",
                    "Débrancher les appareils qui étaient en marche",
                    "Garder réfrigérateur et congélateur fermés",
                    "Ne jamais laisser une bougie allumée sans surveillance",
                    "Pas de réchaud ni de barbecue à l'intérieur (monoxyde de carbone)",
                    "Groupe électrogène : à l'extérieur, disjoncteur coupé",
                    "Ne jamais toucher un fil électrique tombé à terre",
                    "Dépannage Enedis : 09 726 750 suivi des 2 chiffres du département"
                )
            )
        ),
        Template(
            key = "inondation",
            name = "Inondation",
            summary = "Les bons réflexes avant, pendant et après une inondation.",
            sources = listOf(
                "Géorisques, « Que faire en cas d'inondation ? »",
                "ministère de la Transition écologique, « Pluie-inondation : ayons les bons réflexes »",
                "vigilance Météo-France et Vigicrues"
            ),
            sections = listOf(
                section(
                    "Avant",
                    "Suivre la vigilance Météo-France et Vigicrues",
                    "Préparer le kit d'urgence 72 h",
                    "Repérer où couper le gaz et l'électricité",
                    "Monter meubles et objets à l'étage",
                    "Garer la voiture au garage ou l'attacher"
                ),
                section(
                    "Pendant",
                    "Couper gaz, électricité et chauffage sans se mettre en danger",
                    "Se réfugier à l'étage avec papiers, eau et nourriture",
                    "Éviter sous-sols et parkings souterrains",
                    "Ne pas prendre l'ascenseur",
                    "Ne jamais s'engager à pied ou en voiture dans l'eau",
                    "Ne pas aller chercher les enfants à l'école",
                    "Limiter les appels téléphoniques",
                    "Écouter la radio et suivre les consignes"
                ),
                section(
                    "Après",
                    "Attendre l'autorisation avant de rétablir gaz et électricité",
                    "Ne rétablir l'électricité que sur une installation sèche",
                    "Ne pas boire l'eau du robinet avant l'accord des autorités"
                )
            )
        ),
        Template(
            key = "voiture_hiver",
            name = "Kit voiture (hiver)",
            summary = "Équipements et vérifications avant de prendre la route en hiver.",
            sources = listOf(
                "Bison Futé, « La conduite en hiver »",
                "Sécurité routière, équipement de la voiture",
                "ministère de la Transition écologique, « En hiver : les bons réflexes »"
            ),
            sections = listOf(
                section(
                    "Équipements obligatoires",
                    "Gilet haute visibilité à portée de main",
                    "Triangle de présignalisation",
                    "Pneus hiver ou chaînes en zone de montagne (1er novembre – 31 mars)"
                ),
                section(
                    "Kit hiver",
                    "Raclette ou grattoir",
                    "Bombe antigivre",
                    "Lave-glace antigel",
                    "Couverture",
                    "Vêtements chauds et gants",
                    "Eau et nourriture",
                    "Lampe de poche",
                    "Téléphone chargé et chargeur",
                    "Traitements médicaux"
                ),
                section(
                    "Avant de partir",
                    "Consulter la météo sur tout le trajet",
                    "Vérifier l'état des routes (Bison Futé)",
                    "Vérifier le niveau de carburant",
                    "S'entraîner à monter les chaînes"
                )
            )
        ),
        Template(
            key = "trousse",
            name = "Trousse de premiers secours",
            summary = "Le contenu de base d'une trousse de secours pour la maison.",
            sources = listOf("Croix-Rouge française, « Trousse de secours : que doit-elle contenir ? »"),
            sections = listOf(
                section(
                    "Protection",
                    "Gants jetables sans latex"
                ),
                section(
                    "Plaies et brûlures",
                    "Antiseptique",
                    "Sérum physiologique (dosettes)",
                    "Compresses stériles de plusieurs tailles",
                    "Pansements adhésifs de plusieurs tailles",
                    "Sparadrap",
                    "Bandes en rouleau",
                    "Bandage triangulaire",
                    "Hydrogel pour brûlures"
                ),
                section(
                    "Urgences",
                    "Coussin hémostatique",
                    "Couverture de survie"
                ),
                section(
                    "Instruments",
                    "Ciseaux",
                    "Pince à épiler",
                    "Épingles de sûreté",
                    "Thermomètre"
                ),
                section(
                    "Entretien",
                    "Dates de péremption vérifiées (1 fois par an)"
                )
            )
        ),
        Template(
            key = "randonnee",
            name = "Départ en randonnée",
            summary = "Préparer sa sortie et son sac pour randonner en sécurité.",
            sources = listOf(
                "ministère des Sports, « Sortir en montagne »",
                "FFRandonnée, recommandations de sécurité",
                "Croix-Rouge française, trousse de secours de randonnée"
            ),
            sections = listOf(
                section(
                    "Avant de partir",
                    "Consulter la météo (Météo-France)",
                    "Itinéraire adapté au moins expérimenté du groupe",
                    "Itinéraire de repli prévu",
                    "Marge horaire de sécurité",
                    "Itinéraire et heure de retour confiés à un proche",
                    "Éviter de partir seul"
                ),
                section(
                    "Orientation et alerte",
                    "Carte détaillée",
                    "Boussole ou GPS",
                    "Téléphone chargé (urgence : 112)",
                    "Sifflet",
                    "Lampe frontale et piles de rechange"
                ),
                section(
                    "Eau et nourriture",
                    "Eau : au moins 1,5 L par personne",
                    "En-cas et sucre"
                ),
                section(
                    "Vêtements",
                    "Chaussures de randonnée adaptées",
                    "Vêtement chaud et imperméable",
                    "Chapeau, lunettes de soleil et crème solaire"
                ),
                section(
                    "Santé et sécurité",
                    "Trousse de premiers secours",
                    "Couverture de survie",
                    "Médicaments personnels",
                    "Tire-tique",
                    "Répulsif anti-insectes",
                    "Fiche d'urgence (identité, traitements, allergies)",
                    "Couteau ou outil multifonction",
                    "Briquet"
                )
            )
        )
    )

    fun byKey(key: String?): Template? = if (key == null) null else all.firstOrNull { it.key == key }
}
