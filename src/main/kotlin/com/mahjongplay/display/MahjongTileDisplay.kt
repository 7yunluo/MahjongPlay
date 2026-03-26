package com.mahjongplay.display

import com.mahjongplay.MahjongPlayPlugin
import com.mahjongplay.model.MahjongTile
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.Interaction
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.entity.Display
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Quaternionf

object TileConstants {
    const val SCALE = 0.15f

    // Model element is 12x16x8 pixels → 0.75x1x0.5 blocks, then scaled by SCALE
    const val WIDTH = 0.75f * SCALE
    const val HEIGHT = 1f * SCALE
    const val DEPTH = 0.5f * SCALE

    const val PADDING = 0.005f

    const val STICK_WIDTH = 0.15f
    const val STICK_HEIGHT = 0.02f
    const val STICK_DEPTH = 0.02f
}

enum class TileFace { FACE_DOWN, FACE_UP, STANDING }

class MahjongTileDisplay(
    val location: Location,
    val tile: MahjongTile,
    var face: TileFace = TileFace.FACE_DOWN,
    var yaw: Float = 0f,
    val interactive: Boolean = false,
) {
    var entity: ItemDisplay? = null
        private set
    var interactionEntity: Interaction? = null
        private set

    fun spawn(): ItemDisplay {
        val world = location.world
        val display = world.spawnEntity(location, EntityType.ITEM_DISPLAY) as ItemDisplay
        display.isPersistent = false
        display.setViewRange(1.0f)
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE)

        val item = createTileItem(tile)
        display.setItemStack(item)
        applyTransform(display)

        display.setVisibleByDefault(false)
        entity = display

        if (interactive) {
            spawnInteraction()
        }

        return display
    }

    private fun spawnInteraction() {
        val world = location.world
        val interaction = world.spawnEntity(location, EntityType.INTERACTION) as Interaction
        interaction.isPersistent = false
        interaction.interactionWidth = TileConstants.WIDTH + 0.02f
        interaction.interactionHeight = TileConstants.HEIGHT + 0.02f
        interaction.isResponsive = false
        interactionEntity = interaction
    }

    fun updateTile(newTile: MahjongTile) {
        entity?.setItemStack(createTileItem(newTile))
    }

    fun updatePosition(loc: Location, newYaw: Float, newFace: TileFace) {
        yaw = newYaw
        face = newFace
        entity?.let {
            it.teleport(loc)
            applyTransform(it)
        }
        interactionEntity?.teleport(loc)
    }

    fun showTo(player: Player) {
        entity?.let { player.showEntity(MahjongPlayPlugin.instance, it) }
    }

    fun hideTo(player: Player) {
        entity?.let { player.hideEntity(MahjongPlayPlugin.instance, it) }
    }

    fun remove() {
        entity?.remove()
        entity = null
        interactionEntity?.remove()
        interactionEntity = null
    }

    private fun applyTransform(display: ItemDisplay) {
        val matrix = Matrix4f()

        matrix.rotate(Quaternionf(AxisAngle4f(Math.toRadians(yaw.toDouble()).toFloat(), 0f, 1f, 0f)))

        when (face) {
            TileFace.STANDING -> { }
            TileFace.FACE_UP -> {
                matrix.rotate(Quaternionf(AxisAngle4f(Math.toRadians(-90.0).toFloat(), 1f, 0f, 0f)))
            }
            TileFace.FACE_DOWN -> {
                matrix.rotate(Quaternionf(AxisAngle4f(Math.toRadians(90.0).toFloat(), 1f, 0f, 0f)))
            }
        }

        matrix.scale(TileConstants.SCALE)

        display.setTransformationMatrix(matrix)
    }

    companion object {
        @Suppress("UnstableApiUsage")
        fun createTileItem(tile: MahjongTile): ItemStack {
            val item = ItemStack(Material.PAPER)
            item.setData(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData()
                    .addFloat((tile.code + 1).toFloat())
                    .build()
            )
            return item
        }
    }
}
