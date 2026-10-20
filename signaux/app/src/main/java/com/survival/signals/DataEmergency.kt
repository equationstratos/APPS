package com.survival.signals

/*
 * Emergency numbers (France / Europe) and public alert signals. Every fact comes from an official
 * source named in the entry: service-public.fr, ministère de l'Intérieur, ministère de la Mer,
 * DGAC, ARS, arrêté du 23 mars 2007 relatif au signal national d'alerte.
 */

private const val SRC_NUMBERS = "Service-public.fr et ministère de l'Intérieur : numéros d'urgence"
private const val SRC_PSC1 = "Ministère de l'Intérieur : recommandations PSC1"
private const val SRC_SIREN = "Ministère de l'Intérieur : « Comment réagir au signal national d'alerte » ; " +
    "arrêté du 23 mars 2007 relatif au signal national d'alerte"
private const val SRC_FR_ALERT = "Ministère de l'Intérieur et fr-alert.gouv.fr"

private const val G_CALL = "Avant d'appeler"
private const val G_EMERGENCY = "Urgences"
private const val G_SEA_AIR = "Mer, air et alertes"
private const val G_HELP = "Aide et écoute"
private const val G_GOOD_TO_KNOW = "Bon à savoir"

val EMERGENCY_ENTRIES: List<Entry> = listOf(
    Entry(
        id = "num-message",
        section = Section.URGENCES,
        group = G_CALL,
        title = "Que dire aux secours",
        summary = "Votre numéro de téléphone, la nature du problème et le lieu le plus précis possible.",
        details = listOf(
            "Le numéro de téléphone d'où vous appelez.",
            "La nature du problème : malaise, accident, incendie…",
            "La localisation la plus précise possible.",
            "Le nombre de personnes concernées et l'état de chaque victime.",
            "Les risques éventuels (incendie, explosion, effondrement…) et les premières mesures prises.",
            "Répondez aux questions, appliquez les consignes et ne raccrochez que lorsqu'on vous l'indique.",
        ),
        source = SRC_PSC1,
        glyph = "?",
        keywords = "alerte message appel informations raccrocher",
    ),
    Entry(
        id = "num-112",
        section = Section.URGENCES,
        group = G_EMERGENCY,
        title = "Numéro d'urgence européen",
        summary = "Toutes les urgences, dans tous les pays de l'Union européenne. Gratuit, 24 h/24.",
        details = listOf(
            "Joignable depuis un téléphone fixe ou mobile.",
            "Il ne remplace pas les 15, 17 et 18, qui restent joignables directement.",
        ),
        source = SRC_NUMBERS,
        dial = "112",
        keywords = "europe europeen union europeenne secours",
    ),
    Entry(
        id = "num-15",
        section = Section.URGENCES,
        group = G_EMERGENCY,
        title = "SAMU",
        summary = "Urgence médicale : malaise, blessure grave, détresse vitale.",
        source = SRC_NUMBERS,
        dial = "15",
        keywords = "medecin medical ambulance sante malaise",
    ),
    Entry(
        id = "num-17",
        section = Section.URGENCES,
        group = G_EMERGENCY,
        title = "Police secours, gendarmerie",
        summary = "Infraction en cours : violences, agression, vol, cambriolage.",
        source = SRC_NUMBERS,
        dial = "17",
        keywords = "police gendarmerie agression vol violence",
    ),
    Entry(
        id = "num-18",
        section = Section.URGENCES,
        group = G_EMERGENCY,
        title = "Sapeurs-pompiers",
        summary = "Incendie, accident, personne en danger : secours aux personnes.",
        source = SRC_NUMBERS,
        dial = "18",
        keywords = "pompiers incendie feu accident secours",
    ),
    Entry(
        id = "num-114",
        section = Section.URGENCES,
        group = G_EMERGENCY,
        title = "Personnes sourdes ou malentendantes",
        summary = "Urgences par SMS, visio, tchat ou fax, gratuitement et 24 h/24. Toucher ouvre la messagerie.",
        details = listOf(
            "Dans le SMS, indiquez la nature du problème et le lieu le plus précis possible.",
            "Visio et tchat : application « urgence 114 » ou site urgence114.fr.",
            "Une victime de violences qui ne peut pas parler à voix haute peut aussi envoyer un SMS au 114.",
        ),
        source = "Ministère de l'Intérieur : le 114, numéro d'urgence pour les personnes sourdes et malentendantes",
        dial = "114",
        sms = true,
        keywords = "sourd sourde malentendant sms texto visio tchat fax lsf violences silencieux",
    ),
    Entry(
        id = "num-196",
        section = Section.URGENCES,
        group = G_SEA_AIR,
        title = "Urgence en mer (CROSS)",
        summary = "Joindre le CROSS depuis un téléphone. En mer, la VHF canal 16 reste prioritaire.",
        details = listOf(
            "Gratuit, 24 h/24 et 7 j/7.",
            "Surtout utile depuis la terre, pour signaler une personne ou un bateau en difficulté en mer.",
            "Sur la plage ou dans la zone de baignade, faites plutôt le 112.",
        ),
        source = "Ministère de la Mer : « Comment alerter les secours en mer ? » ; SNSM",
        dial = "196",
        keywords = "mer bateau naufrage cross noyade marin sauvetage",
    ),
    Entry(
        id = "num-191",
        section = Section.URGENCES,
        group = G_SEA_AIR,
        title = "Urgence aéronautique",
        summary = "Aéronef en détresse, accident d'avion ou d'hélicoptère, aéronef disparu.",
        details = listOf(
            "Gratuit, 24 h/24, réservé aux urgences aéronautiques.",
            "Il joint le centre de coordination de sauvetage aéronautique (ARCC Lyon Mont-Verdun).",
            "Balise de détresse d'aéronef déclenchée par erreur : prévenez le 191 pour éviter des recherches inutiles.",
        ),
        source = "Ministère de la Transition écologique (DGAC) : « Faites le 191 pour les urgences aéronautiques »",
        dial = "191",
        keywords = "avion helicoptere aeronef crash planeur ulm arcc balise",
    ),
    Entry(
        id = "num-197",
        section = Section.URGENCES,
        group = G_SEA_AIR,
        title = "Alerte attentat, alerte enlèvement",
        summary = "Activé seulement quand une alerte est diffusée, pour transmettre une information utile aux enquêteurs.",
        details = listOf(
            "Gratuit, 24 h/24 pendant l'alerte.",
            "Pour une urgence, faites le 17 ou le 112.",
        ),
        source = "Ministère de l'Intérieur : Ma Sécurité",
        dial = "197",
        keywords = "attentat enlevement temoignage terrorisme",
    ),
    Entry(
        id = "num-115",
        section = Section.URGENCES,
        group = G_HELP,
        title = "Samu social",
        summary = "Hébergement d'urgence, personne sans abri en détresse.",
        source = SRC_NUMBERS,
        dial = "115",
        keywords = "sans abri sdf hebergement social froid",
    ),
    Entry(
        id = "num-119",
        section = Section.URGENCES,
        group = G_HELP,
        title = "Enfance en danger",
        summary = "Enfant en danger ou en risque de l'être.",
        source = SRC_NUMBERS,
        dial = "119",
        keywords = "enfant maltraitance mineur",
    ),
    Entry(
        id = "num-116000",
        section = Section.URGENCES,
        group = G_HELP,
        title = "Enfants disparus",
        summary = "Disparition d'un enfant.",
        source = SRC_NUMBERS,
        dial = "116000",
        display = "116 000",
        keywords = "enfant disparu disparition fugue",
    ),
    Entry(
        id = "num-116117",
        section = Section.URGENCES,
        group = G_HELP,
        title = "Médecin de garde",
        summary = "Soins non urgents le soir, le week-end et les jours fériés. Selon les régions ; " +
            "en cas de doute sur la gravité, faites le 15.",
        source = "Service-public.fr ; agences régionales de santé",
        dial = "116117",
        display = "116 117",
        keywords = "medecin garde nuit weekend permanence soins",
    ),
    Entry(
        id = "num-3114",
        section = Section.URGENCES,
        group = G_HELP,
        title = "Prévention du suicide",
        summary = "Écoute par des professionnels du soin, gratuite, 24 h/24 et 7 j/7. Aussi pour l'entourage.",
        source = "3114.fr : numéro national de prévention du suicide",
        dial = "3114",
        keywords = "suicide detresse psychologique ecoute",
    ),
    Entry(
        id = "num-3919",
        section = Section.URGENCES,
        group = G_HELP,
        title = "Violences Femmes Info",
        summary = "Écoute et orientation, gratuite et anonyme. Ce n'est pas un numéro d'urgence : " +
            "en cas de danger immédiat, faites le 17 ou le 112.",
        source = "arretonslesviolences.gouv.fr",
        dial = "3919",
        keywords = "violences conjugales femmes couple ecoute",
    ),
    Entry(
        id = "num-0800112112",
        section = Section.URGENCES,
        group = G_GOOD_TO_KNOW,
        title = "Numéro de rappel des secours",
        summary = "Depuis le 1er octobre 2024, les services d'urgence vous rappellent depuis ce numéro : répondez.",
        details = listOf(
            "Il ne reçoit pas les appels d'urgence : pour alerter, faites le 15, 17, 18 ou 112.",
        ),
        source = "Ministère de l'Intérieur (2024)",
        display = "0 800 112 112",
        keywords = "rappel 0800112112 appel manque",
    ),
)

private const val G_SIRENS = "Sirènes"
private const val G_PHONE = "Sur le téléphone"
private const val G_REFLEXES = "Réflexes"

val ALERT_ENTRIES: List<Entry> = listOf(
    Entry(
        id = "alerte-sna",
        section = Section.ALERTE,
        group = G_SIRENS,
        title = "Signal national d'alerte",
        summary = "Son modulé : 3 cycles de 1 min 41 s, séparés par 5 s de silence. " +
            "Mettez-vous immédiatement à l'abri.",
        details = listOf(
            "Rejoignez sans délai un bâtiment et appliquez les réflexes de cet onglet.",
            "Ne quittez pas l'abri avant le signal de fin d'alerte ou une consigne des autorités.",
            "Essai : le premier mercredi de chaque mois à midi, un seul cycle de 1 min 41 s.",
        ),
        source = SRC_SIREN,
        figure = Figure.TIMELINE_SIREN,
        glyph = "3×",
        keywords = "sirene sna alarme saip essai mercredi midi",
    ),
    Entry(
        id = "alerte-fin",
        section = Section.ALERTE,
        group = G_SIRENS,
        title = "Fin d'alerte",
        summary = "Son continu de 30 secondes : le danger est écarté.",
        details = listOf(
            "Vous pouvez quitter l'abri, sauf consigne contraire des autorités.",
        ),
        source = SRC_SIREN,
        figure = Figure.TIMELINE_SIREN_END,
        glyph = "30 s",
        keywords = "sirene fin alerte son continu",
    ),
    Entry(
        id = "alerte-essai",
        section = Section.ALERTE,
        group = G_SIRENS,
        title = "Essai mensuel des sirènes",
        summary = "Le premier mercredi de chaque mois à midi : un seul cycle de 1 min 41 s. Ce n'est qu'un essai.",
        source = SRC_SIREN,
        glyph = "12 h",
        keywords = "sirene essai test mercredi midi",
    ),
    Entry(
        id = "alerte-barrage",
        section = Section.ALERTE,
        group = G_SIRENS,
        title = "Corne de brume : barrage",
        summary = "Rupture de barrage ou lâcher d'eau important : sons de 2 s séparés de 3 s, pendant au moins 2 minutes.",
        details = listOf(
            "Gagnez immédiatement, à pied, les points hauts les plus proches prévus par le plan de l'ouvrage.",
            "À défaut, montez dans les étages supérieurs d'un immeuble élevé et solide.",
            "Ne prenez pas l'ascenseur et ne revenez pas sur vos pas.",
            "Attendez la fin d'alerte (son continu de 30 s) ou les consignes des autorités pour redescendre.",
            "Essai : les premiers mercredis de mars, juin, septembre et décembre à 12 h 15 " +
                "(cycle de 12 s : trois sons de 2 s séparés de 3 s).",
        ),
        source = "Ministère de l'Intérieur : signal d'alerte spécifique aux ouvrages hydrauliques ; " +
            "arrêté du 23 mars 2007",
        figure = Figure.TIMELINE_DAM,
        glyph = "2 s",
        keywords = "barrage digue inondation corne brume eau",
    ),
    Entry(
        id = "alerte-fralert",
        section = Section.ALERTE,
        group = G_PHONE,
        title = "FR-Alert",
        summary = "Notification sur les téléphones présents dans la zone en danger, avec un son spécifique, " +
            "même en mode silencieux.",
        details = listOf(
            "Aucune application ni inscription : il suffit d'un téléphone allumé.",
            "Rien n'est reçu si le téléphone est éteint ou en mode avion.",
            "L'alerte passe par les antennes relais : l'État n'a pas accès à la position des téléphones.",
            "FR-Alert complète les sirènes et ne les remplace pas.",
            "Appliquez les consignes indiquées dans le message.",
        ),
        source = SRC_FR_ALERT,
        glyph = "FR",
        keywords = "fralert notification sms telephone portable cell broadcast",
    ),
    Entry(
        id = "alerte-abri",
        section = Section.ALERTE,
        group = G_REFLEXES,
        title = "Se mettre à l'abri",
        summary = "Rejoignez sans délai un bâtiment. Ne restez pas dans un véhicule.",
        details = listOf(
            "Éloignez-vous des fenêtres et ne les ouvrez pas pour regarder dehors.",
            "Ne prenez pas l'ascenseur.",
            "En cas de confinement, arrêtez la ventilation, le chauffage et la climatisation, et bouchez " +
                "les ouvertures (fentes, portes, aérations, cheminées…).",
            "Ne quittez pas l'abri sans consigne des autorités.",
        ),
        source = SRC_SIREN,
        glyph = "1",
        keywords = "abri confinement batiment voiture",
    ),
    Entry(
        id = "alerte-info",
        section = Section.ALERTE,
        group = G_REFLEXES,
        title = "S'informer",
        summary = "Écoutez la radio (Radio France, radios locales) ou France Télévisions, et suivez les consignes.",
        details = listOf(
            "En situation de crise, le téléphone et internet peuvent ne plus fonctionner : " +
                "la radio hertzienne a le plus de chances de résister.",
            "Consultez aussi les sites et comptes officiels de l'État et de la préfecture.",
        ),
        source = SRC_SIREN,
        glyph = "2",
        keywords = "radio information consignes prefecture television",
    ),
    Entry(
        id = "alerte-enfants",
        section = Section.ALERTE,
        group = G_REFLEXES,
        title = "Les enfants à l'école",
        summary = "N'allez pas les chercher : ils y sont protégés par leurs enseignants.",
        source = SRC_SIREN,
        glyph = "3",
        keywords = "enfants ecole enseignants",
    ),
    Entry(
        id = "alerte-telephone",
        section = Section.ALERTE,
        group = G_REFLEXES,
        title = "Téléphoner",
        summary = "N'appelez qu'en cas d'urgence vitale, pour laisser les réseaux disponibles pour les secours.",
        source = SRC_SIREN,
        glyph = "4",
        keywords = "telephone reseau saturation appel",
    ),
)
