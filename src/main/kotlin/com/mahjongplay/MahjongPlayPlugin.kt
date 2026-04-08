package com.mahjongplay

import com.mahjongplay.interaction.EntityInteractionListener
import com.mahjongplay.table.MahjongCommand
import com.mahjongplay.table.MahjongTableManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import fr.skytasul.glowingentities.GlowingEntities

class MahjongPlayPlugin : JavaPlugin(), Listener {

    companion object {
        lateinit var instance: MahjongPlayPlugin
            private set
    }

    lateinit var tableManager: MahjongTableManager
        private set

    lateinit var glowingEntities: GlowingEntities
        private set

    override fun onEnable() {
        instance = this
        glowingEntities = GlowingEntities(this)
        tableManager = MahjongTableManager()

        getCommand("mahjong")?.let {
            val cmd = MahjongCommand(tableManager)
            it.setExecutor(cmd)
            it.tabCompleter = cmd
        }

        server.pluginManager.registerEvents(EntityInteractionListener(tableManager), this)
        server.pluginManager.registerEvents(this, this)

        server.scheduler.runTaskLater(this, Runnable {
            tableManager.loadTables(dataFolder)
        }, 20L)

        logger.info("MahjongCraft v${pluginMeta.version} enabled!")
    }

    override fun onDisable() {
        if (::glowingEntities.isInitialized) {
            glowingEntities.disable()
        }
        if (::tableManager.isInitialized) {
            tableManager.saveTables(dataFolder)
            tableManager.shutdown()
        }
        logger.info("MahjongCraft disabled.")
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId.toString()
        tableManager.leaveTable(uuid)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (tableManager.isProtectedBlock(event.block.location)) {
            event.isCancelled = true
        }
    }
}
