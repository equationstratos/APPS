package com.survival.signals

/*
 * Visual, sound and body signals. Official sources: ICAO Annex 12 (ground-air code for survivors),
 * COLREG / RIPAM Annex IV (distress signals at sea), FFME and Sapeurs-pompiers de France (helicopter gestures),
 * Swiss Alpine Club (alpine distress signal). Common practices are flagged as unofficial.
 */

private const val SRC_ANNEX_12 = "OACI, Annexe 12 (recherches et sauvetage), appendice : " +
    "code de signaux visuels sol-air à l'usage des survivants"
private const val SRC_RIPAM = "RIPAM (COLREG), règle 37 et annexe IV : signaux de détresse"
private const val SRC_GESTURES = "Fédération française de la montagne et de l'escalade (FFME) : fiche « Alerter » ; " +
    "Sapeurs-pompiers de France : dossier montagne"
private const val SRC_ALPINE = "Club alpin suisse (CAS) : signal de détresse alpin"

private const val SIZE_RULE = "Au moins 2,5 m de long, aussi visible que possible."

private const val G_SURVIVORS = "Code des survivants"
private const val G_HOW_TO = "Réalisation et réponse"

val GROUND_AIR_ENTRIES: List<Entry> = listOf(
    Entry(
        id = "sol-v",
        section = Section.SOL_AIR,
        group = G_SURVIVORS,
        title = "Besoin d'assistance",
        summary = "V : nous avons besoin d'aide.",
        details = listOf(SIZE_RULE),
        source = SRC_ANNEX_12,
        glyph = "V",
        big = listOf("V", "Besoin d'assistance"),
        figure = Figure.GROUND_V,
        keywords = "aide secours require assistance lettre v",
    ),
    Entry(
        id = "sol-x",
        section = Section.SOL_AIR,
        group = G_SURVIVORS,
        title = "Besoin d'assistance médicale",
        summary = "X : il nous faut une aide médicale.",
        details = listOf(SIZE_RULE),
        source = SRC_ANNEX_12,
        glyph = "X",
        big = listOf("X", "Assistance médicale"),
        figure = Figure.GROUND_X,
        keywords = "medecin blesse medical lettre x",
    ),
    Entry(
        id = "sol-n",
        section = Section.SOL_AIR,
        group = G_SURVIVORS,
        title = "Non (négatif)",
        summary = "N : non.",
        details = listOf(SIZE_RULE),
        source = SRC_ANNEX_12,
        glyph = "N",
        big = listOf("N", "Non"),
        figure = Figure.GROUND_N,
        keywords = "non negatif no lettre n",
    ),
    Entry(
        id = "sol-y",
        section = Section.SOL_AIR,
        group = G_SURVIVORS,
        title = "Oui (affirmatif)",
        summary = "Y : oui.",
        details = listOf(SIZE_RULE),
        source = SRC_ANNEX_12,
        glyph = "Y",
        big = listOf("Y", "Oui"),
        figure = Figure.GROUND_Y,
        keywords = "oui affirmatif yes lettre y",
    ),
    Entry(
        id = "sol-fleche",
        section = Section.SOL_AIR,
        group = G_SURVIVORS,
        title = "Nous partons dans cette direction",
        summary = "Flèche : nous nous déplaçons dans la direction indiquée.",
        details = listOf(SIZE_RULE),
        source = SRC_ANNEX_12,
        glyph = "↑",
        figure = Figure.GROUND_ARROW,
        keywords = "fleche direction depart marche",
    ),
    Entry(
        id = "sol-realisation",
        section = Section.SOL_AIR,
        group = G_HOW_TO,
        title = "Comment tracer les signaux",
        summary = "Symboles d'au moins 2,5 m de long, aussi visibles que possible.",
        details = listOf(
            "Tout matériau convient : bandes de tissu, toile de parachute, morceaux de bois, pierres…",
            "On peut aussi tracer les symboles en piétinant le sol ou en le tachant, par exemple avec de l'huile.",
            "Attirez l'attention sur vos signaux par d'autres moyens : radio, fusées, fumée, lumière réfléchie.",
        ),
        source = SRC_ANNEX_12,
        glyph = "2,5 m",
        keywords = "taille dimension materiaux tissu pierres bois tracer",
    ),
    Entry(
        id = "sol-reponse",
        section = Section.SOL_AIR,
        group = G_HOW_TO,
        title = "Réponse de l'aéronef",
        summary = "Message compris : de jour, l'avion balance ses ailes ; de nuit, il allume et éteint deux fois " +
            "ses phares d'atterrissage.",
        details = listOf(
            "Sans phares d'atterrissage, il allume et éteint deux fois ses feux de navigation.",
            "Sans ce signal, votre message n'a pas été compris.",
        ),
        source = SRC_ANNEX_12,
        glyph = "OK",
        keywords = "reponse avion ailes phares compris",
    ),
)

private const val G_AIRCRAFT = "Hélicoptère ou avion"
private const val G_SEA = "En mer"

val GESTURE_ENTRIES: List<Entry> = listOf(
    Entry(
        id = "geste-oui",
        section = Section.GESTES,
        group = G_AIRCRAFT,
        title = "Oui : j'ai besoin de secours",
        summary = "Les deux bras levés en Y (comme « Yes »).",
        details = listOf(
            "Restez immobile, dos au vent, les deux bras bien levés en Y au-dessus du corps.",
            "N'agitez pas les bras : des randonneurs qui saluent l'hélicoptère brouillent le message.",
            "Portez un vêtement bien visible, rouge ou blanc si possible.",
        ),
        source = SRC_GESTURES,
        figure = Figure.BODY_YES,
        keywords = "y yes oui bras leves helicoptere aide",
    ),
    Entry(
        id = "geste-non",
        section = Section.GESTES,
        group = G_AIRCRAFT,
        title = "Non : je n'ai pas besoin de secours",
        summary = "Un bras levé, l'autre baissé, en diagonale : un N (comme « No »).",
        source = SRC_GESTURES,
        figure = Figure.BODY_NO,
        keywords = "n no non bras diagonale helicoptere",
    ),
    Entry(
        id = "geste-mer",
        section = Section.GESTES,
        group = G_SEA,
        title = "Détresse en mer",
        summary = "Bras tendus de chaque côté, levés et abaissés lentement et de façon répétée.",
        details = listOf("Signal de détresse officiel en mer (RIPAM, annexe IV)."),
        source = SRC_RIPAM,
        figure = Figure.BODY_SEA,
        keywords = "bras tendus lever abaisser mer bateau detresse",
    ),
)

private const val G_UNIVERSAL = "Signaux universels"
private const val G_BE_SEEN = "Se faire voir"
private const val G_AT_SEA = "En mer"

val DISTRESS_ENTRIES: List<Entry> = listOf(
    Entry(
        id = "detresse-sos",
        section = Section.DETRESSE,
        group = G_UNIVERSAL,
        title = "SOS en morse",
        summary = "··· −−− ··· : trois brefs, trois longs, trois brefs, enchaînés. Puis une pause, et on recommence.",
        details = listOf(
            "Par tout moyen : lampe, sifflet, coups frappés, radio…",
            "Un signal long dure trois fois un signal bref.",
            "Signal de détresse reconnu en mer (RIPAM, annexe IV).",
        ),
        source = "$SRC_RIPAM ; UIT-R M.1677-1 (code Morse international)",
        glyph = "SOS",
        big = listOf("SOS", "··· −−− ···"),
        figure = Figure.TIMELINE_SOS,
        keywords = "sos morse lampe sifflet lumiere",
    ),
    Entry(
        id = "detresse-alpin",
        section = Section.DETRESSE,
        group = G_UNIVERSAL,
        title = "Signal de détresse alpin",
        summary = "6 signaux par minute (un toutes les 10 s), puis 1 minute de pause, et on recommence.",
        details = listOf(
            "Signal sonore (sifflet, cris) ou lumineux (lampe).",
            "Réponse des secours : 3 signaux par minute, puis 1 minute de pause.",
            "Le guide ci-dessous indique le moment de chaque signal.",
        ),
        source = SRC_ALPINE,
        glyph = "6/min",
        figure = Figure.TIMELINE_ALPINE_CALL,
        tool = Tool.ALPINE_CADENCE,
        keywords = "montagne alpin sifflet lampe six minute randonnee",
    ),
    Entry(
        id = "detresse-alpin-reponse",
        section = Section.DETRESSE,
        group = G_UNIVERSAL,
        title = "Réponse au signal alpin",
        summary = "3 signaux par minute (un toutes les 20 s), puis 1 minute de pause : votre appel a été reçu.",
        source = SRC_ALPINE,
        glyph = "3/min",
        figure = Figure.TIMELINE_ALPINE_REPLY,
        keywords = "montagne alpin reponse trois minute recu",
    ),
    Entry(
        id = "detresse-trois",
        section = Section.DETRESSE,
        group = G_UNIVERSAL,
        title = "Règle des trois",
        summary = "Trois signaux identiques : 3 feux, 3 coups de sifflet, 3 éclats de lumière… " +
            "puis une pause, et on recommence.",
        details = listOf(
            "Feux disposés en triangle ou en ligne, bien séparés.",
            "En montagne, en Europe, utilisez de préférence le signal de détresse alpin.",
        ),
        source = "Be Ready Utah (sécurité civile de l'Utah) ; Pennsylvania Game Commission",
        glyph = "3",
        figure = Figure.FIRES_TRIANGLE,
        unofficial = "Usage courant, non officiel : consigne diffusée notamment par des services nord-américains.",
        keywords = "trois feux triangle sifflet coups",
    ),
    Entry(
        id = "detresse-miroir",
        section = Section.DETRESSE,
        group = G_BE_SEEN,
        title = "Miroir de signalisation",
        summary = "Renvoyez l'éclat du soleil vers l'aéronef ou le navire en visant entre deux doigts.",
        details = listOf(
            "Tendez le bras et formez un V avec deux doigts, la cible au creux du V.",
            "Inclinez le miroir pour que la tache de soleil tombe sur vos doigts.",
            "Faites passer et repasser la tache sur vos doigts : elle balaie aussi la cible.",
            "Gardez le miroir près de l'œil et les doigts le plus loin possible du visage.",
            "Un miroir de signalisation à trou de visée est plus précis.",
        ),
        source = "$SRC_ANNEX_12 (lumière réfléchie) ; guides de survie (technique de visée)",
        figure = null,
        unofficial = "Technique courante, non normalisée. L'OACI cite la lumière réfléchie parmi les moyens " +
            "d'attirer l'attention.",
        keywords = "miroir soleil reflet eclat viser",
    ),
    Entry(
        id = "detresse-fumee",
        section = Section.DETRESSE,
        group = G_BE_SEEN,
        title = "Fumée et feu",
        summary = "Une fumée bien visible attire l'attention : cherchez le contraste avec le fond.",
        details = listOf(
            "Végétation verte jetée sur un feu bien pris : épaisse fumée blanche, visible sur un fond sombre.",
            "Caoutchouc, plastique ou produits pétroliers : fumée noire, plus visible sur la neige.",
            "La fumée se disperse par vent, pluie ou neige : elle est plus efficace par temps calme.",
            "N'allumez un feu que si c'est sans danger : attention au risque d'incendie.",
            "En mer, le fumigène orange est un signal de détresse officiel (RIPAM, annexe IV).",
        ),
        source = "Pennsylvania Game Commission ; $SRC_RIPAM",
        unofficial = "Techniques courantes, non officielles, sauf le fumigène orange en mer.",
        keywords = "fumee feu fumigene orange blanche noire",
    ),
    Entry(
        id = "detresse-fusees",
        section = Section.DETRESSE,
        group = G_BE_SEEN,
        title = "Fusées et feux à main",
        summary = "Rouge = détresse : fusée à parachute ou feu à main rouges, fusées à étoiles rouges tirées une à une.",
        details = listOf(
            "Couleurs de détresse dans le RIPAM : la lumière rouge et la fumée orange, et aucune autre.",
            "Les artifices complètent l'appel radio ou téléphonique, sans le remplacer.",
            "Leur usage est interdit hors détresse.",
        ),
        source = "$SRC_RIPAM ; SNSM",
        keywords = "fusee parachute feu main rouge artifice pyrotechnie",
    ),
    Entry(
        id = "detresse-ripam",
        section = Section.DETRESSE,
        group = G_AT_SEA,
        title = "Signaux de détresse en mer",
        summary = "Utilisés ensemble ou séparément, ils signifient détresse et besoin d'assistance.",
        details = listOf(
            "Coups de canon ou autres signaux explosifs, à environ une minute d'intervalle.",
            "Son continu d'un appareil de signalisation de brume.",
            "Fusées ou bombes à étoiles rouges, lancées une à une à courts intervalles.",
            "SOS (··· −−− ···) par tout moyen de signalisation.",
            "Le mot MAYDAY en radiotéléphonie.",
            "Les pavillons N et C du Code international de signaux.",
            "Un pavillon carré avec, au-dessus ou au-dessous, une boule ou un objet analogue.",
            "Des flammes sur le navire (baril de goudron ou d'huile en feu, par exemple).",
            "Fusée à parachute ou feu à main produisant une lumière rouge.",
            "Signal fumigène produisant une fumée orange.",
            "Bras tendus de chaque côté, levés et abaissés lentement et de façon répétée.",
            "Alerte ASN (VHF canal 70, ondes moyennes ou courtes) ou alerte par satellite.",
            "Signaux des balises de détresse et des transpondeurs radar de survie.",
            "Il est interdit d'utiliser ces signaux hors détresse.",
        ),
        source = SRC_RIPAM,
        glyph = "NC",
        keywords = "ripam colreg mer bateau navire pavillon canon corne",
    ),
)
