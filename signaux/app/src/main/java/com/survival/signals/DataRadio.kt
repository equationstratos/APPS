package com.survival.signals

/*
 * Radio procedures and frequencies. Official sources: ANFR (CRR maritime handbook), ITU Radio
 * Regulations, ICAO Annex 10, EU SERA, Cospas-Sarsat, ECC/CEPT (PMR446), IARU Region 1 band plan.
 * Channel uses that are customs rather than rules are flagged as unofficial.
 */

private const val SRC_ANFR_CRR = "ANFR : manuel de préparation au certificat restreint de radiotéléphoniste (CRR) maritime"

private const val G_MESSAGES = "Messages"
private const val G_FREQUENCIES = "Fréquences et canaux"

/** "446,00625 à 446,09375 MHz" for channels [from]..[to]. */
private fun pmrRange(from: Int, to: Int): String =
    "${formatMhz(pmrChannelMhz(from), 5)} à ${formatMhz(pmrChannelMhz(to), 5)} MHz"

val RADIO_ENTRIES: List<Entry> = listOf(
    Entry(
        id = "radio-mayday",
        section = Section.RADIO,
        group = G_MESSAGES,
        title = "MAYDAY : détresse",
        summary = "Danger grave et imminent, besoin d'une aide immédiate. Priorité absolue, VHF canal 16.",
        details = listOf(
            "MAYDAY MAYDAY MAYDAY (se prononce « m'aider »).",
            "ICI, puis le nom du navire (3 fois) et l'indicatif d'appel épelé une fois.",
            "MAYDAY, puis le nom du navire et l'indicatif.",
            "Position : absolue, ou par rapport à un point connu.",
            "Nature de la détresse : incendie, voie d'eau, homme à la mer…",
            "Assistance demandée.",
            "Nombre de personnes à bord.",
            "Intentions du responsable et tout renseignement utile aux secours.",
            "À VOUS. Répétez le message jusqu'à ce qu'une station réponde.",
        ),
        source = "$SRC_ANFR_CRR ; SNSM",
        steps = true,
        tool = Tool.MAYDAY_FORM,
        keywords = "mayday detresse vhf canal 16 bateau navire appel m'aider message",
    ),
    Entry(
        id = "radio-panpan",
        section = Section.RADIO,
        group = G_MESSAGES,
        title = "PAN PAN : urgence",
        summary = "Urgence sans danger imminent : avarie, remorquage, blessé ou malade à bord. Priorité 2.",
        details = listOf(
            "PAN PAN (3 fois), puis le nom de la station appelée ou « À TOUS » (3 fois), ICI et le nom du navire.",
            "Le message commence par la position.",
        ),
        source = SRC_ANFR_CRR,
        steps = true,
        keywords = "pan urgence remorquage avarie blesse malade",
    ),
    Entry(
        id = "radio-securite",
        section = Section.RADIO,
        group = G_MESSAGES,
        title = "SÉCURITÉ",
        summary = "Danger pour la navigation ou avis météo : objet dangereux, épave à la dérive, phare éteint, " +
            "coup de vent. Priorité 3.",
        details = listOf("L'appel commence par SÉCURITÉ, répété 3 fois."),
        source = SRC_ANFR_CRR,
        keywords = "securite navigation meteo epave avis",
    ),
    Entry(
        id = "radio-mayday-air",
        section = Section.RADIO,
        group = G_MESSAGES,
        title = "MAYDAY en aviation",
        summary = "Sur la fréquence air-sol en cours, dans cet ordre si possible. Transpondeur : 7700.",
        details = listOf(
            "MAYDAY MAYDAY MAYDAY.",
            "Station appelée, si le temps le permet.",
            "Identification de l'aéronef.",
            "Nature de la détresse.",
            "Intentions du commandant de bord.",
            "Position, niveau et cap.",
        ),
        source = "OACI, Annexe 10, vol. II ; règlement européen SERA (SERA.14095)",
        steps = true,
        keywords = "avion pilote aeronef transpondeur 7700 squawk",
    ),
    Entry(
        id = "radio-tout-moyen",
        section = Section.RADIO,
        group = G_MESSAGES,
        title = "En détresse, tout moyen est permis",
        summary = "Une station en détresse peut utiliser tout moyen radio à sa disposition pour attirer l'attention, " +
            "signaler sa position et obtenir de l'aide.",
        source = "UIT : Règlement des radiocommunications, article 30 (voir aussi le n° 4.9)",
        glyph = "!",
        keywords = "reglement uit tout moyen licence",
    ),
    Entry(
        id = "radio-vhf16",
        section = Section.RADIO,
        group = G_FREQUENCIES,
        title = "VHF marine : canal 16",
        summary = "156,800 MHz : détresse, urgence, sécurité et appel. Le CROSS se joint en priorité par ce canal.",
        details = listOf(
            "Canal 70 (156,525 MHz) : réservé à l'ASN, l'appel sélectif numérique.",
            "Bouton DISTRESS d'une VHF ASN : alerte automatique avec le MMSI et la position (si un GPS est relié) ; " +
                "le CROSS répond ensuite sur le canal 16.",
            "Le MMSI, identifiant de la VHF, est attribué par l'ANFR.",
            "Depuis un téléphone : 196.",
        ),
        source = "ANFR ; ministère de la Mer ; UIT (Règlement des radiocommunications, appendice 18)",
        glyph = "16",
        big = listOf("Canal 16", "156,800 MHz"),
        keywords = "vhf canal 16 70 asn dsc mmsi cross marine 156,8 156,525",
    ),
    Entry(
        id = "radio-1215",
        section = Section.RADIO,
        group = G_FREQUENCIES,
        title = "121,5 MHz : urgence aéronautique",
        summary = "Fréquence internationale d'urgence de l'aviation.",
        details = listOf(
            "La plupart des balises 406 MHz émettent aussi sur 121,5 MHz : ce signal de radioralliement guide " +
                "les secours sur les derniers kilomètres.",
            "Les satellites ne traitent plus les balises 121,5 MHz depuis le 1er février 2009.",
        ),
        source = "OACI, Annexe 10 ; Cospas-Sarsat",
        glyph = "121,5",
        big = listOf("121,5 MHz"),
        keywords = "aviation urgence frequence avion balise radioralliement",
    ),
    Entry(
        id = "radio-406",
        section = Section.RADIO,
        group = G_FREQUENCIES,
        title = "Balises 406 MHz (Cospas-Sarsat)",
        summary = "Balises de détresse par satellite : EPIRB en mer, ELT en aviation, PLB personnelles.",
        details = listOf(
            "Position à environ 100 m près si la balise intègre un GPS, sinon à environ 5 km.",
            "La plupart émettent aussi sur 121,5 MHz pour le radioralliement.",
            "Une balise enregistrée permet aux secours de joindre son propriétaire.",
            "Balise d'aéronef déclenchée par erreur : prévenez le 191.",
        ),
        source = "Cospas-Sarsat ; NOAA SARSAT ; DGAC (191)",
        glyph = "406",
        big = listOf("406 MHz"),
        keywords = "balise epirb elt plb satellite cospas sarsat gps",
    ),
    Entry(
        id = "radio-pmr446",
        section = Section.RADIO,
        group = G_FREQUENCIES,
        title = "PMR446 (talkie-walkie)",
        summary = "16 canaux, 500 mW, sans licence. Aucun canal d'urgence officiel.",
        details = listOf(
            "Canaux 1 à 8 : ${pmrRange(1, 8)}.",
            "Canaux 9 à 16 : ${pmrRange(9, PMR_CHANNELS)}.",
            "Canaux espacés de 12,5 kHz ; appareil portatif à antenne intégrée, utilisé d'appareil à appareil.",
            "Aucun canal n'est réservé aux secours : rien ne garantit que quelqu'un vous écoute.",
        ),
        source = "ECC / CEPT : décision ECC/DEC/(15)05 (PMR446)",
        glyph = "446",
        unofficial = "Non officiel : certains réseaux bénévoles, comme Rete Radio Montana en Italie, ont leur propre " +
            "canal d'appel. Convenez à l'avance d'un canal et d'un sous-code (CTCSS) avec vos compagnons.",
        keywords = "pmr talkie walkie 446 canal sous code ctcss montagne rete radio montana",
    ),
    Entry(
        id = "radio-cb",
        section = Section.RADIO,
        group = G_FREQUENCIES,
        title = "CB 27 MHz",
        summary = "40 canaux de 26,965 à 27,405 MHz, utilisation libre avec un appareil conforme.",
        details = listOf(
            "Canal 9 (27,065 MHz) : traditionnellement réservé aux appels d'urgence.",
            "Canal 19 (27,185 MHz) : canal des routiers.",
            "La CB ne bénéficie d'aucune protection contre les brouillages.",
        ),
        source = "ANFR ; usages des cibistes",
        glyph = "27",
        unofficial = "Les rôles des canaux 9 et 19 sont des usages courants, non officiels.",
        keywords = "cb cibi citizen band routiers canal 9 19 27 mhz",
    ),
    Entry(
        id = "radio-145500",
        section = Section.RADIO,
        group = G_FREQUENCIES,
        title = "Radioamateur : 145,500 MHz",
        summary = "Fréquence d'appel FM de la bande des 2 m. Réservée aux radioamateurs titulaires d'un certificat.",
        details = listOf(
            "Fixée par le plan de bandes de l'IARU Région 1, dont font partie l'Europe et l'Afrique.",
            "En détresse, tout moyen radio disponible peut être utilisé (voir « En détresse, tout moyen est permis »).",
        ),
        source = "IARU Région 1 : plan de bandes VHF",
        glyph = "145,5",
        big = listOf("145,500 MHz"),
        keywords = "radioamateur amateur 2 m vhf appel 145,5 iaru",
    ),
)
