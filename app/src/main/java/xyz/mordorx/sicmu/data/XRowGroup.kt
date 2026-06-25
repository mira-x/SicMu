package xyz.mordorx.sicmu.data

/**
 * This is a subclass of `Row` that is foldable/collapsable and may contain
 * children `Row` elements.
 */
data class XRowGroup(
    override val level: Int,
    //override val parent: XRow?,
    override val containingFolder: String,
    override val fileName: String,
    override val isSelected: Boolean = false,
    val isFolded: Boolean = true,
    /** get recursive number of songs (excluding RowGroup) inside this group */
    val songCount: Int,
    val durationMs: Long,
) : XRow {

}
