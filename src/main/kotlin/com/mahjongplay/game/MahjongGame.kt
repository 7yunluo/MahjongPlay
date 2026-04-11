package com.mahjongplay.game

import com.mahjongplay.model.*
import kotlinx.coroutines.*
import org.mahjong4j.GeneralSituation
import org.mahjong4j.PersonalSituation
import org.mahjong4j.hands.Kantsu
import org.mahjong4j.tile.TileType
import java.util.UUID

enum class GameStatus { WAITING, PLAYING }

interface GameEventListener {
    fun onGameStart(game: MahjongGame) {}
    fun onRoundStart(game: MahjongGame, round: MahjongRound) {}
    fun onTileDrawn(player: MahjongPlayerBase, tile: MahjongTile) {}
    fun onTileDiscarded(player: MahjongPlayerBase, tile: MahjongTile) {}
    fun onChii(player: MahjongPlayerBase, claimedTile: MahjongTile, from: MahjongPlayerBase) {}
    fun onPon(player: MahjongPlayerBase, claimedTile: MahjongTile, from: MahjongPlayerBase) {}
    fun onKan(player: MahjongPlayerBase, tile: MahjongTile, kanType: String, from: MahjongPlayerBase? = null) {}
    fun onRiichi(player: MahjongPlayerBase, tile: MahjongTile) {}
    fun onTsumo(player: MahjongPlayerBase, tile: MahjongTile, settlement: YakuSettlement) {}
    fun onRon(winners: List<MahjongPlayerBase>, loser: MahjongPlayerBase, tile: MahjongTile, settlements: List<YakuSettlement>) {}
    fun onDraw(draw: ExhaustiveDraw, settlement: ScoreSettlement) {}
    fun onScoreSettlement(settlement: ScoreSettlement) {}
    fun onGameEnd(game: MahjongGame, scoreList: List<ScoreItem>) {}
    fun onHandsUpdated(player: MahjongPlayerBase) {}
}

class MahjongGame(
    val tableId: UUID = UUID.randomUUID(),
    var rule: MahjongRule = MahjongRule(),
    var listener: GameEventListener? = null
) {
    var status = GameStatus.WAITING
        private set

    private val isPlaying: Boolean get() = status == GameStatus.PLAYING

    val players = ArrayList<MahjongPlayerBase>(4)
    val realPlayers: List<MahjongPlayer> get() = players.filterIsInstance<MahjongPlayer>()

    var seat = mutableListOf<MahjongPlayerBase>()
    var round: MahjongRound = MahjongRound()

    private var gameJob: Job? = null

    private var wall: MutableList<MahjongTile> = mutableListOf()
    private var deadWall: MutableList<MahjongTile> = mutableListOf()
    var doraIndicators: MutableList<MahjongTile> = mutableListOf()
        private set
    var uraDoraIndicators: MutableList<MahjongTile> = mutableListOf()
        private set
    private var allDiscards: MutableList<MahjongTile> = mutableListOf()
    private var kanCount: Int = 0

    private val playerCount: Int get() = rule.playerCount
    private val isSanma: Boolean get() = rule.isSanma

    private val seatOrderFromDealer: List<MahjongPlayerBase>
        get() = List(playerCount) { seat[(round.round + it) % playerCount] }

    val wallSize: Int get() = wall.size

    private val generalSituation: GeneralSituation
        get() {
            val bakaze = round.wind.tile
            val dora = doraIndicators.map { it.doraFromIndicator(isSanma).mahjong4jTile }
            val uradora = uraDoraIndicators.map { it.doraFromIndicator(isSanma).mahjong4jTile }
            return GeneralSituation(isFirstRound, wall.isEmpty(), bakaze, dora, uradora)
        }

    private val isFirstRound: Boolean
        get() = players.all { it.discardedTiles.size <= 1 } && players.all { it.fuuroList.isEmpty() } && players.all { it.nukiDoraTiles.isEmpty() }

    private val isHoutei: Boolean get() = wall.isEmpty()

    private val isSuufonRenda: Boolean
        get() {
            if (isSanma) return false
            if (!isFirstRound) return false
            val allDiscarded = players.flatMap { it.discardedTiles }
            if (allDiscarded.size != 4) return false
            val first = allDiscarded[0].mahjong4jTile
            return allDiscarded.all { it.mahjong4jTile == first } && first.type == TileType.FONPAI
        }

    // --- Lifecycle ---

    fun addBot(name: String = "Bot") {
        if (players.size >= playerCount) return
        players += MahjongBot(displayName = name)
    }

    fun join(playerUUID: String, playerName: String): Boolean {
        if (status != GameStatus.WAITING || players.size >= playerCount) return false
        if (players.any { it.uuid == playerUUID }) return false
        players += MahjongPlayer(uuid = playerUUID, displayName = playerName)
        return true
    }

    fun leave(playerUUID: String) {
        if (isPlaying) { end(); return }
        val isHost = players.firstOrNull()?.uuid == playerUUID
        if (isHost) {
            val newHost = players.find { it.isRealPlayer && it.uuid != playerUUID }
            if (newHost != null) { players.remove(newHost); players.add(0, newHost); newHost.ready = true }
            else players.removeAll { it is MahjongBot }
        }
        players.removeIf { it.uuid == playerUUID }
    }

    fun readyOrNot(playerUUID: String, ready: Boolean) { players.find { it.uuid == playerUUID }?.ready = ready }
    fun kick(index: Int) { if (index in players.indices) players.removeAt(index) }

    fun changeRules(newRule: MahjongRule) {
        rule = newRule
        players.forEachIndexed { i, p -> if (i != 0 && p is MahjongPlayer) p.ready = false }
    }

    fun start() {
        if (players.size != playerCount || !players.all { it.ready }) return
        status = GameStatus.PLAYING
        seat = players.toMutableList().apply { shuffle() }
        round = rule.length.getStartingRound()
        players.forEach {
            it.points = rule.startingPoints
            it.basicThinkingTime = rule.thinkingTime.base
            it.extraThinkingTime = rule.thinkingTime.extra
        }
        listener?.onGameStart(this)
        gameJob = CoroutineScope(Dispatchers.Default).launch {
            delay(500)
            startRound()
        }
    }

    fun end() {
        status = GameStatus.WAITING
        gameJob?.cancel()
        val scoreList = players.map { ScoreItem(it.displayName, it.uuid, it.isRealPlayer, scoreOrigin = it.points, scoreChange = 0) }
        listener?.onGameEnd(this, scoreList)
        seat.clear(); clearStuffs(); round = MahjongRound()
    }

    // --- Internal helpers ---

    private fun clearStuffs() {
        players.forEach {
            it.riichi = false; it.doubleRiichi = false
            it.hands.clear(); it.fuuroList.clear()
            it.discardedTiles.clear(); it.discardedTilesForDisplay.clear()
            it.riichiSengenTile = null; it.nukiDoraTiles.clear()
            it.lastDiscardAllIndex = -1; it.riichiAllIndex = -1
        }
        wall.clear(); deadWall.clear(); doraIndicators.clear(); uraDoraIndicators.clear()
        allDiscards.clear(); kanCount = 0
    }

    private fun generateWall() {
        wall = if (isSanma) {
            when (rule.redFive) {
                MahjongRule.RedFive.NONE -> MahjongTile.sanmaWall
                MahjongRule.RedFive.THREE -> MahjongTile.sanmaRedFive3Wall
                MahjongRule.RedFive.FOUR -> MahjongTile.sanmaRedFive4Wall
            }
        } else {
            when (rule.redFive) {
                MahjongRule.RedFive.NONE -> MahjongTile.normalWall
                MahjongRule.RedFive.THREE -> MahjongTile.redFive3Wall
                MahjongRule.RedFive.FOUR -> MahjongTile.redFive4Wall
            }
        }.shuffled().toMutableList()
    }

    private fun assignDeadWall() {
        deadWall = wall.takeLast(14).toMutableList()
        repeat(14) { wall.removeLast() }
        doraIndicators.clear(); doraIndicators += deadWall[4]
        uraDoraIndicators.clear(); uraDoraIndicators += deadWall[5]
    }

    private fun dealHands() {
        val order = seatOrderFromDealer
        repeat(3) { order.forEach { p -> repeat(4) { p.drawTile(wall.removeFirst()) } } }
        order.forEach { it.drawTile(wall.removeFirst()) }
        order[0].drawTile(wall.removeFirst())
    }

    private fun drawRinshanTile(player: MahjongPlayerBase, addDora: Boolean = true): MahjongTile {
        val tile = deadWall.removeFirst()
        player.drawTile(tile)
        if (wall.isNotEmpty()) deadWall += wall.removeLast()
        if (addDora) {
            kanCount++
            val idx = 4 + (kanCount - 1) * 2
            if (idx < deadWall.size) doraIndicators += deadWall[idx]
            if (idx + 1 < deadWall.size) uraDoraIndicators += deadWall[idx + 1]
        }
        return tile
    }

    private fun sortHands(player: MahjongPlayerBase, lastTile: MahjongTile? = null) {
        if (!player.autoArrangeHands) return
        if (lastTile != null) {
            val last = player.hands.removeLast()
            player.hands.sortBy { it.sortOrder }
            player.hands += last
        } else {
            player.hands.sortBy { it.sortOrder }
        }
    }

    private fun MahjongPlayerBase.isKyuushuKyuuhai(): Boolean = isFirstRound && numbersOfYaochuuhaiTypes >= 9

    private fun MahjongPlayerBase.asClaimTarget(target: MahjongPlayerBase): ClaimTarget {
        val si = seat.indexOf(this)
        val pc = playerCount
        return when (target) {
            seat[(si + 1) % pc] -> ClaimTarget.RIGHT
            seat[(si + pc - 1) % pc] -> ClaimTarget.LEFT
            else -> ClaimTarget.ACROSS
        }
    }

    private fun MahjongPlayerBase.getPersonalSituation(
        isTsumo: Boolean = false, isChankan: Boolean = false, isRinshanKaihoh: Boolean = false
    ): PersonalSituation {
        val selfWind = seatOrderFromDealer.indexOf(this)
        val jikaze = Wind.entries[selfWind].tile
        val isIppatsu = isIppatsu(players, allDiscards)
        return PersonalSituation(isTsumo, isIppatsu, riichi, doubleRiichi, isChankan, isRinshanKaihoh, jikaze)
    }

    private fun claimTargetBySeatDiff(claimerSeat: Int, discarderSeat: Int): ClaimTarget {
        val pc = playerCount
        return when ((discarderSeat - claimerSeat + pc) % pc) {
            1 -> ClaimTarget.RIGHT
            pc - 1 -> ClaimTarget.LEFT
            else -> ClaimTarget.ACROSS
        }
    }

    private fun addChiiCannotDiscardTiles(tile: MahjongTile, pair: Pair<MahjongTile, MahjongTile>, out: MutableList<MahjongTile>) {
        val codes = listOf(tile.mahjong4jTile.code, pair.first.mahjong4jTile.code, pair.second.mahjong4jTile.code).sorted()
        val idx = codes.indexOf(tile.mahjong4jTile.code)
        val num = tile.mahjong4jTile.number
        if (idx == 0 && num + 3 <= 9) out += tile.nextTile.nextTile.nextTile
        if (idx == 2 && num - 3 >= 1) out += tile.previousTile.previousTile.previousTile
    }

    // --- Round loop ---

    private suspend fun startRound(clearRiichiSticks: Boolean = true) {
        if (!isPlaying) return
        clearStuffs()
        if (clearRiichiSticks) players.forEach { it.riichiStickCount = 0 }
        listener?.onRoundStart(this, round)
        generateWall(); assignDeadWall(); dealHands()
        players.forEach { sortHands(it); listener?.onHandsUpdated(it) }

        val dealer = seatOrderFromDealer[0]
        var dealerRemaining = false
        var clearNextRiichiSticks = true
        var roundDraw: ExhaustiveDraw? = null
        delay(1500)

        var nextPlayer: MahjongPlayerBase = dealer
        var needDraw = true
        var drewTile: Boolean
        val cannotDiscard = mutableListOf<MahjongTile>()

        roundLoop@ while (isPlaying) {
            val player = nextPlayer
            val si = seat.indexOf(player)
            val isDealer = dealer == player
            var timeoutTile = player.hands.last()

            if (needDraw) {
                player.justDrewTile = true
                val lastTile = if (isDealer && player.discardedTiles.isEmpty()) {
                    player.hands.last()
                } else {
                    wall.removeFirst().also {
                        player.drawTile(it); sortHands(player, it)
                        listener?.onTileDrawn(player, it); listener?.onHandsUpdated(player)
                    }
                }
                drewTile = true
                if (player is MahjongBot) delay(500)

                val canWin = player.canWin(lastTile, true, rule = rule, generalSituation = generalSituation, personalSituation = player.getPersonalSituation(isTsumo = true))
                if (canWin && player.askToTsumo()) {
                    player.tsumo(tile = lastTile)
                    if (isDealer) dealerRemaining = true
                    break@roundLoop
                }

                if (player.isKyuushuKyuuhai() && player.askToKyuushuKyuuhai()) {
                    roundDraw = ExhaustiveDraw.KYUUSHU_KYUUHAI; break@roundLoop
                }

                var lastKitaRinshan: MahjongTile? = null
                if (isSanma) {
                    kitaLoop@ while (player.canKita && deadWall.isNotEmpty()) {
                        if (!player.askToKita()) break@kitaLoop
                        val north = player.declareKita()
                        listener?.onHandsUpdated(player)

                        val ronOnKita = canRonList(north, player)
                        if (ronOnKita.isNotEmpty()) {
                            val rp = ronOnKita[0]
                            if (rp.askToRon(north, rp.asClaimTarget(player))) {
                                mutableListOf(rp).ron(target = player, tile = north)
                                if (dealer == rp) dealerRemaining = true
                                break@roundLoop
                            }
                        }

                        val rinshan = drawRinshanTile(player, addDora = false)
                        sortHands(player, rinshan)
                        lastKitaRinshan = rinshan
                        timeoutTile = rinshan
                        listener?.onTileDrawn(player, rinshan); listener?.onHandsUpdated(player)

                        if (player.canWin(rinshan, true, rule = rule, generalSituation = generalSituation, personalSituation = player.getPersonalSituation(isTsumo = true, isRinshanKaihoh = true))) {
                            player.tsumo(isRinshanKaihoh = true, tile = rinshan)
                            if (isDealer) dealerRemaining = true
                            break@roundLoop
                        }
                    }
                }

                var finalRinshan: MahjongTile? = null
                kanLoop@ while ((player.canKakan || player.canAnkan) && !isHoutei && kanCount < 4) {
                    val kanTile = player.askToAnkanOrKakan(rule) ?: break@kanLoop
                    val isAnkan = kanTile in player.tilesCanAnkan
                    if (isAnkan) player.ankan(kanTile) else player.kakan(kanTile)
                    listener?.onKan(player, kanTile, if (isAnkan) "ankan" else "kakan")

                    val chanList = if (isAnkan) canChanAnkanList(kanTile, player) else canChanKakanList(kanTile, player)
                    if (chanList.isNotEmpty()) {
                        if (chanList.size > 1) {
                            chanList.ron(target = player, isChankan = true, tile = kanTile)
                            if (dealer in chanList) dealerRemaining = true
                            break@roundLoop
                        }
                        val cp = chanList[0]
                        if (cp.askToRon(kanTile, cp.asClaimTarget(player))) {
                            mutableListOf(cp).ron(target = player, isChankan = true, tile = kanTile)
                            if (dealer == cp) dealerRemaining = true
                            break@roundLoop
                        }
                    }

                    val rinshan = drawRinshanTile(player)
                    sortHands(player, rinshan)
                    listener?.onTileDrawn(player, rinshan); listener?.onHandsUpdated(player)

                    if (player.canWin(rinshan, true, rule = rule, generalSituation = generalSituation, personalSituation = player.getPersonalSituation(isTsumo = true, isRinshanKaihoh = true))) {
                        player.tsumo(isRinshanKaihoh = true, tile = rinshan)
                        if (isDealer) dealerRemaining = true
                        break@roundLoop
                    }
                    if (kanCount >= 4) {
                        val pkc = player.fuuroList.count { it.mentsu is Kantsu }
                        if (pkc != kanCount) { roundDraw = ExhaustiveDraw.SUUKAIKAN; break@roundLoop }
                    }
                    finalRinshan = rinshan
                }
                timeoutTile = finalRinshan ?: lastKitaRinshan ?: lastTile
            } else {
                player.justDrewTile = false
                drewTile = false; needDraw = true
            }

            val riichiSengen = if (!isHoutei && player.isRiichiable) player.askToRiichi() else null

            val isRiichiLocked = player.riichi || player.doubleRiichi
            if (isRiichiLocked) {
                cannotDiscard += player.hands; cannotDiscard.removeAll { it == timeoutTile }
            }

            val toDiscard = riichiSengen
                ?: if (isRiichiLocked) timeoutTile
                else player.askToDiscardTile(timeoutTile, cannotDiscard, drewTile)
            val discarded = player.discardTile(toDiscard) ?: break@roundLoop
            allDiscards += discarded
            player.lastDiscardAllIndex = allDiscards.size - 1
            sortHands(player)
            listener?.onTileDiscarded(player, discarded); listener?.onHandsUpdated(player)
            cannotDiscard.clear()

            if (isSuufonRenda) { roundDraw = ExhaustiveDraw.SUUFON_RENDA; break@roundLoop }

            // ron
            val ronList = canRonList(discarded, player)
            if (ronList.isNotEmpty()) {
                if (ronList.size > 1) {
                    ronList.ron(target = player, tile = discarded)
                    if (dealer in ronList) dealerRemaining = true
                    break@roundLoop
                }
                val rp = ronList[0]
                if (rp.askToRon(discarded, rp.asClaimTarget(player))) {
                    mutableListOf(rp).ron(target = player, tile = discarded)
                    if (dealer == rp) dealerRemaining = true
                    break@roundLoop
                }
            }

            if (riichiSengen != null) {
                player.riichi(discarded, isFirstRound); player.riichiStickCount++
                player.riichiAllIndex = player.lastDiscardAllIndex
                listener?.onRiichi(player, discarded)
                if (players.count { it.riichi || it.doubleRiichi } == 4 && !isSanma) {
                    roundDraw = ExhaustiveDraw.SUUCHA_RIICHI; break@roundLoop
                }
            }

            // minkan/pon
            val mkpList = canMinKanOrPonList(discarded, player, si)
            var someoneKan = false
            if (mkpList.isNotEmpty()) {
                val mp = mkpList[0]
                val ct = claimTargetBySeatDiff(seat.indexOf(mp), si)
                when (mp.askToMinkanOrPon(discarded, mp.asClaimTarget(player), rule)) {
                    MahjongGameBehavior.PON -> {
                        mp.pon(discarded, ct, player); listener?.onPon(mp, discarded, player)
                        listener?.onHandsUpdated(mp); nextPlayer = mp; needDraw = false; cannotDiscard += discarded; someoneKan = true
                    }
                    MahjongGameBehavior.MINKAN -> {
                        mp.minkan(discarded, ct, player); listener?.onKan(mp, discarded, "minkan", player)
                        val rt = drawRinshanTile(mp); sortHands(mp, rt)
                        listener?.onTileDrawn(mp, rt); listener?.onHandsUpdated(mp)
                        if (mp.canWin(rt, true, rule = rule, generalSituation = generalSituation, personalSituation = mp.getPersonalSituation(isTsumo = true, isRinshanKaihoh = true))) {
                            mp.tsumo(isRinshanKaihoh = true, tile = rt)
                            if (mp == dealer) dealerRemaining = true; break@roundLoop
                        }
                        if (kanCount >= 4 && mp.fuuroList.count { it.mentsu is Kantsu } != kanCount) {
                            roundDraw = ExhaustiveDraw.SUUKAIKAN; break@roundLoop
                        }
                        nextPlayer = mp; needDraw = false; someoneKan = true
                    }
                    else -> {}
                }
            }

            val ponList = canPonList(discarded, player).toMutableList().also { it -= mkpList.toSet() }
            val chiiList = canChiiList(discarded, player, si).toMutableList()
            var someonePon = false

            if (!someoneKan && ponList.isNotEmpty()) {
                repeat(playerCount) { off ->
                    val idx = (si + off) % playerCount; val pp = seat[idx]
                    if (pp in ponList) {
                        if (pp in chiiList) {
                            val res = pp.askToPonOrChii(discarded, pp.getTilePairsForChii(discarded), pp.getTilePairForPon(discarded), pp.asClaimTarget(player))
                            if (res != null) {
                                val ct2 = claimTargetBySeatDiff(idx, si)
                                if (res.first == res.second) { pp.pon(discarded, ct2, player); listener?.onPon(pp, discarded, player); cannotDiscard += discarded }
                                else { pp.chii(discarded, res, player); listener?.onChii(pp, discarded, player); addChiiCannotDiscardTiles(discarded, res, cannotDiscard) }
                                listener?.onHandsUpdated(pp); nextPlayer = pp; needDraw = false; someonePon = true; return@repeat
                            } else chiiList -= pp
                        } else {
                            val pct = claimTargetBySeatDiff(idx, si)
                            if (pp.askToPon(discarded, pp.getTilePairForPon(discarded), pct)) {
                                pp.pon(discarded, pct, player); listener?.onPon(pp, discarded, player)
                                listener?.onHandsUpdated(pp); nextPlayer = pp; needDraw = false; cannotDiscard += discarded; someonePon = true; return@repeat
                            }
                        }
                    }
                }
            }

            var someoneChii = false
            if (!someoneKan && !someonePon && chiiList.isNotEmpty()) {
                val cp = chiiList[0]
                val cr = cp.askToChii(discarded, cp.getTilePairsForChii(discarded), cp.asClaimTarget(player))
                if (cr != null) {
                    cp.chii(discarded, cr, player); listener?.onChii(cp, discarded, player)
                    listener?.onHandsUpdated(cp); nextPlayer = cp; needDraw = false
                    cannotDiscard += discarded; addChiiCannotDiscardTiles(discarded, cr, cannotDiscard); someoneChii = true
                }
            }

            if (mkpList.isEmpty() && ponList.isEmpty() && chiiList.isEmpty()) delay(MIN_WAITING_TIME)
            if (!someoneKan && !someonePon && !someoneChii) nextPlayer = seat[(si + 1) % playerCount]
            if (wall.isEmpty()) { roundDraw = ExhaustiveDraw.NORMAL; break@roundLoop }
        }

        if (roundDraw != null) {
            dealerRemaining = if (roundDraw == ExhaustiveDraw.NORMAL) {
                val nagashi = canNagashiManganList()
                if (nagashi.isNotEmpty()) nagashi.nagashiMangan()
                dealer.isTenpai
            } else true
            clearNextRiichiSticks = false
            roundDraw(roundDraw!!)
        }

        delay(3000)
        if (!isPlaying) return

        if (!round.isAllLast(rule)) {
            if (dealerRemaining) round.honba++ else round.nextRound(playerCount)
            startRound(clearRiichiSticks = clearNextRiichiSticks)
        } else {
            if (players.none { it.points >= rule.minPointsToWin }) {
                if (dealerRemaining) { round.honba++; startRound(clearRiichiSticks = clearNextRiichiSticks) }
                else {
                    val fr = rule.length.getFinalRound(playerCount)
                    if (round.wind == fr.first && round.round == fr.second) end()
                    else { round.nextRound(playerCount); startRound(clearRiichiSticks = clearNextRiichiSticks) }
                }
            } else end()
        }
    }

    // --- Win/draw logic ---

    private fun canRonList(tile: MahjongTile, discardedPlayer: MahjongPlayerBase, isChanKan: Boolean = false): List<MahjongPlayerBase> =
        players.filter {
            if (it == discardedPlayer) return@filter false
            if (it.discardedTiles.isEmpty() && it.isMenzenchin) return@filter false
            it.canWin(tile, false, rule = rule, generalSituation = generalSituation, personalSituation = it.getPersonalSituation(isChankan = isChanKan)) && !it.isFuriten(tile, allDiscards)
        }

    private fun canChanKakanList(tile: MahjongTile, p: MahjongPlayerBase) = canRonList(tile, p, true)
    private fun canChanAnkanList(tile: MahjongTile, p: MahjongPlayerBase) = canChanKakanList(tile, p).filter { it.isKokushimuso(tile.mahjong4jTile) }
    private fun canPonList(tile: MahjongTile, dp: MahjongPlayerBase) = if (!isHoutei) players.filter { it != dp && it.canPon(tile) } else emptyList()
    private fun canMinKanOrPonList(tile: MahjongTile, dp: MahjongPlayerBase, si: Int) = if (kanCount < 4 && !isHoutei) players.filter { it != dp && it.canMinkan(tile) } else emptyList()
    private fun canChiiList(tile: MahjongTile, dp: MahjongPlayerBase, si: Int) = if (isSanma || isHoutei) emptyList() else players.filter { it != dp && it.canChii(tile) && it == seat[(si + 1) % playerCount] }
    private fun canNagashiManganList() = if (wall.isEmpty()) players.filter { it.discardedTiles == it.discardedTilesForDisplay && it.discardedTiles.all { t -> t.mahjong4jTile.isYaochu } } else emptyList()

    private suspend fun List<MahjongPlayerBase>.ron(target: MahjongPlayerBase, isChankan: Boolean = false, tile: MahjongTile) {
        val yakuList = mutableListOf<YakuSettlement>()
        val scoreList = mutableListOf<ScoreItem>()
        val honba = round.honba * 300
        var total = honba
        val allRiichi = players.sumOf { it.riichiStickCount }
        val extra = allRiichi * ScoringStick.P1000.point + honba
        val fromTarget = List(playerCount) { seat[(seat.indexOf(target) + it) % playerCount] }
        val atamahane = fromTarget.find { it in this }

        forEach {
            val s = it.calcYakuSettlementForWin(tile, false, rule, generalSituation, it.getPersonalSituation(isChankan = isChankan), doraIndicators, uraDoraIndicators)
            yakuList += s
            val rp = if (it.riichi || it.doubleRiichi) 1000 else 0
            val bs = s.score
            val sc = bs - rp + if (it == atamahane) extra else 0
            scoreList += ScoreItem(it.displayName, it.uuid, it.isRealPlayer, scoreOrigin = it.points, scoreChange = sc)
            it.points += sc; total += bs
        }
        val trp = if (target.riichi || target.doubleRiichi) 1000 else 0
        scoreList += ScoreItem(target.displayName, target.uuid, target.isRealPlayer, scoreOrigin = target.points, scoreChange = -(total + trp))
        target.points -= (total + trp)
        (players.toMutableList() - this.toSet() - target).forEach {
            val rp = if (it.riichi || it.doubleRiichi) 1000 else 0
            scoreList += ScoreItem(it.displayName, it.uuid, it.isRealPlayer, scoreOrigin = it.points, scoreChange = -rp)
            it.points -= rp
        }
        listener?.onRon(this, target, tile, yakuList)
        listener?.onScoreSettlement(ScoreSettlement("ron", scoreList))
        delay(YAKU_SETTLE_MS * yakuList.size + SCORE_SETTLE_MS)
    }

    private suspend fun MahjongPlayerBase.tsumo(isRinshanKaihoh: Boolean = false, tile: MahjongTile) {
        val allRiichi = players.sumOf { it.riichiStickCount }
        val honba = round.honba * 100
        val myRp = if (riichi || doubleRiichi) 1000 else 0
        val isDlr = this == seatOrderFromDealer[0]
        val s = calcYakuSettlementForWin(tile, true, rule, generalSituation, getPersonalSituation(isTsumo = true, isRinshanKaihoh = isRinshanKaihoh), doraIndicators, uraDoraIndicators)
        val scoreEnum = if (s.yakuList.isEmpty() && (s.yakumanList.isNotEmpty() || s.doubleYakumanList.isNotEmpty())) {
            org.mahjong4j.Score.calculateYakumanScore(isDlr, s.yakumanList.size + s.doubleYakumanList.size * 2)
        } else {
            org.mahjong4j.Score.calculateScore(isDlr, s.han, s.fu)
        }
        val losers = players.filter { it != this }
        val scoreList = mutableListOf<ScoreItem>()
        var winnerGain = 0
        losers.forEach {
            val rp = if (it.riichi || it.doubleRiichi) 1000 else 0
            val baseShare = if (isDlr) scoreEnum.parentTsumo
            else { if (it == seatOrderFromDealer[0]) scoreEnum.parent else scoreEnum.child }
            val loss = baseShare + honba + rp
            scoreList += ScoreItem(it.displayName, it.uuid, it.isRealPlayer, scoreOrigin = it.points, scoreChange = -loss)
            it.points -= loss
            winnerGain += loss
        }
        val extra = allRiichi * 1000
        winnerGain += extra - myRp
        scoreList.add(0, ScoreItem(displayName, uuid, isRealPlayer, scoreOrigin = points, scoreChange = winnerGain))
        points += winnerGain
        listener?.onTsumo(this, tile, s)
        listener?.onScoreSettlement(ScoreSettlement("tsumo", scoreList))
        delay(YAKU_SETTLE_MS + SCORE_SETTLE_MS)
    }

    private suspend fun List<MahjongPlayerBase>.nagashiMangan() {
        val dealer = seatOrderFromDealer[0]
        val orig = players.associateWith { it.points }
        val allRiichi = players.sumOf { it.riichiStickCount }
        val honba = round.honba * 300
        val extra = allRiichi * 1000 + honba
        val atama = seatOrderFromDealer.find { it in this }
        forEach {
            val bs = if (it == dealer) 12000 else 8000
            it.points += bs + if (it == atama) extra else 0
            val losers = players.filter { p -> p != it }
            losers.forEach { p -> p.points -= bs / losers.size; if (it == atama) p.points -= honba / losers.size }
        }
        val scoreList = players.map { ScoreItem(it.displayName, it.uuid, it.isRealPlayer, scoreOrigin = orig[it]!!, scoreChange = it.points - orig[it]!!) }
        listener?.onScoreSettlement(ScoreSettlement("tsumo", scoreList))
        delay(YAKU_SETTLE_MS * size + SCORE_SETTLE_MS)
    }

    private suspend fun roundDraw(draw: ExhaustiveDraw) {
        val scoreList = buildList<ScoreItem> {
            if (draw != ExhaustiveDraw.NORMAL) {
                players.forEach {
                    val rp = if (it.riichi || it.doubleRiichi) 1000 else 0
                    this += ScoreItem(it.displayName, it.uuid, it.isRealPlayer, scoreOrigin = it.points, scoreChange = -rp)
                    it.points -= rp
                }
            } else {
                val tc = players.count { it.isTenpai }
                if (tc == 0 || tc == playerCount) players.forEach { this += ScoreItem(it.displayName, it.uuid, it.isRealPlayer, scoreOrigin = it.points, scoreChange = 0) }
                else {
                    val nc = playerCount - tc; val nb = 3000 / nc; val bg = 3000 / tc
                    players.forEach {
                        if (it.isTenpai) {
                            val rp = if (it.riichi || it.doubleRiichi) 1000 else 0
                            this += ScoreItem(it.displayName, it.uuid, it.isRealPlayer, scoreOrigin = it.points, scoreChange = bg - rp)
                            it.points += bg; it.points -= rp
                        } else {
                            this += ScoreItem(it.displayName, it.uuid, it.isRealPlayer, scoreOrigin = it.points, scoreChange = -nb)
                            it.points -= nb
                        }
                    }
                }
            }
        }
        delay(3000)
        val settlement = ScoreSettlement(draw.name.lowercase(), scoreList)
        listener?.onDraw(draw, settlement); listener?.onScoreSettlement(settlement)
        delay(SCORE_SETTLE_MS)
    }

    fun getMachiAndHan(player: MahjongPlayerBase, tile: MahjongTile): Map<MahjongTile, Int> {
        if (deadWall.isEmpty()) return emptyMap()
        return player.calculateMachiAndHan(
            hands = player.hands.toMutableList().apply { remove(tile) },
            rule = rule, generalSituation = generalSituation,
            personalSituation = player.getPersonalSituation()
        )
    }

    companion object {
        const val MIN_WAITING_TIME = 1200L
        const val STICKS_PER_STACK = 5
        private const val SCORE_SETTLE_MS = 5000L
        private const val YAKU_SETTLE_MS = 8000L
    }
}
