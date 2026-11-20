package com.dantech.splitvibe.designsystem.components

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes

/**
 * The icons of what can be done with a bill, as the approved designs draw them: 24dp, in lines with
 * round ends, apart from the spark of the AI, which is filled. Each is decorative unless the button it
 * is on has no text, which then needs a label of its own.
 */
object SvActionIcons {
    /** The corners of a page with a line across it: scanning a bill. */
    val Scan: ImageVector = lineIcon(
        "Scan",
        "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M7 12h10",
        ACTION_ICONS,
    )

    /** A camera: taking a photo. */
    val Camera: ImageVector = lineIcon(
        "Camera",
        "M4 8h3l1.5-2.5h7L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z" + circle(12f, 13.5f, 3.5f),
        ACTION_ICONS,
    )

    /** A picture with a mountain and the sun: choosing photos. */
    val Photos: ImageVector = lineIcon(
        "Photos",
        "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z" + circle(8.5f, 10f, 1.5f) +
            "m4 17 5-4.5 4 3.5 3-2.5 4 3.5",
        ACTION_ICONS,
    )

    /** Two sparks: what the AI reads or works out. */
    val Spark: ImageVector = icon("Spark", ACTION_ICONS) {
        addPath(
            pathData = addPathNodes(
                "M12 2l2.2 6.3L20.5 10.5l-6.3 2.2L12 19l-2.2-6.3L3.5 10.5l6.3-2.2L12 2z" +
                    "M19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8L19 16z",
            ),
            fill = IconInk,
        )
    }

    /** A pencil: changing something. */
    val Pencil: ImageVector = lineIcon("Pencil", "M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4", ACTION_ICONS, lineWidth = 2.2f)

    /** A bin: removing something. */
    val Bin: ImageVector = lineIcon("Bin", "M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12M10 11v5M14 11v5", ACTION_ICONS, lineWidth = 2.2f)

    /** A plus: adding something. */
    val Plus: ImageVector = lineIcon("Plus", "M12 5v14M5 12h14", ACTION_ICONS, lineWidth = 2.6f)

    /** Three dots joined by lines: sharing. */
    val Share: ImageVector = lineIcon(
        "Share",
        circle(6f, 12f, 2.5f) + circle(17.5f, 6f, 2.5f) + circle(17.5f, 18f, 2.5f) + "m8.2 10.8 7-3.6M8.2 13.2l7 3.6",
        ACTION_ICONS,
        lineWidth = 2.2f,
    )

    /** A paper plane: sending a message. */
    val Send: ImageVector = lineIcon("Send", "M4 12l16-8-6 16-2.5-6.5L4 12z", ACTION_ICONS, lineWidth = 2.4f)

    /** An arrow up: asking for what was typed. */
    val Ask: ImageVector = lineIcon("Ask", "M12 19V5m-6 6 6-6 6 6", ACTION_ICONS, lineWidth = 2.6f)
}

private const val ACTION_ICONS = "SvActionIcons"
