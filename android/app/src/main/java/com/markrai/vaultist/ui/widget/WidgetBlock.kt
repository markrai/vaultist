package com.markrai.vaultist.ui.widget

sealed interface WidgetBlock {
    val stableId: Long

    data class Heading(
        val level: Int,
        val text: String,
        override val stableId: Long,
    ) : WidgetBlock

    data class Paragraph(
        val text: String,
        override val stableId: Long,
    ) : WidgetBlock

    data class ListItem(
        val text: String,
        val ordered: Boolean,
        override val stableId: Long,
    ) : WidgetBlock

    data class Quote(
        val text: String,
        override val stableId: Long,
    ) : WidgetBlock

    data class Code(
        val text: String,
        override val stableId: Long,
    ) : WidgetBlock
}

data class NoteWidgetContent(
    val title: String,
    val blocks: List<WidgetBlock>,
)
