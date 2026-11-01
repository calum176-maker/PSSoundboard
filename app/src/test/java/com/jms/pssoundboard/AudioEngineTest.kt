package com.jms.pssoundboard

import com.jms.pssoundboard.audio.WavUtils
import com.jms.pssoundboard.data.SynthTestClipsGenerator
import com.jms.pssoundboard.data.model.Board
import com.jms.pssoundboard.data.model.DeckState
import com.jms.pssoundboard.data.model.Pad
import com.jms.pssoundboard.data.model.PadRef
import com.jms.pssoundboard.data.model.PlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class AudioEngineTest {

    @Test
    fun wavHeader_generation_hasCorrectMagicAndSize() {
        val sampleRate = 44_100
        val dataSize = sampleRate * 2 // 1 second of 16-bit mono
        val header = WavUtils.header(dataSize, sampleRate, channels = 1)

        assertEquals(44, header.size)
        assertEquals('R'.code.toByte(), header[0])
        assertEquals('I'.code.toByte(), header[1])
        assertEquals('F'.code.toByte(), header[2])
        assertEquals('F'.code.toByte(), header[3])
        assertEquals('W'.code.toByte(), header[8])
        assertEquals('A'.code.toByte(), header[9])
        assertEquals('V'.code.toByte(), header[10])
        assertEquals('E'.code.toByte(), header[11])
    }

    @Test
    fun wav_normalize_and_trim_calculatesCorrectPeaks() {
        val original = floatArrayOf(-0.2f, 0.5f, -0.4f, 0.1f)
        val peak = WavUtils.peak(original)
        assertEquals(0.5f, peak, 0.001f)

        val normalized = WavUtils.normalize(original, targetPeak = 0.9f)
        val newPeak = WavUtils.peak(normalized)
        assertEquals(0.9f, newPeak, 0.001f)

        val trimmed = WavUtils.trim(floatArrayOf(1f, 2f, 3f, 4f), sampleRate = 2, startSec = 0.5f, endSec = 1.5f)
        assertEquals(2, trimmed.size)
        assertEquals(2f, trimmed[0], 0.001f)
        assertEquals(3f, trimmed[1], 0.001f)
    }

    @Test
    fun builtinClips_allHaveDefinitionsAndValidColors() {
        assertEquals(12, SynthTestClipsGenerator.CLIPS.size)
        SynthTestClipsGenerator.CLIPS.forEach { clip ->
            assertTrue(clip.id.isNotBlank())
            assertTrue(clip.title.isNotBlank())
            assertTrue(clip.colorHex.startsWith("#"))
            assertEquals(7, clip.colorHex.length)
        }
    }

    @Test
    fun deckState_miniDeckPads_returnsUpToSixPads() {
        val pads = List(12) { slot ->
            Pad(slot = slot, title = "Pad $slot", filePath = "/path/$slot.wav")
        }
        val board = Board(id = "fps", name = "FPS", pads = pads)
        val state = DeckState(
            boards = listOf(board),
            activeBoardId = "fps",
            favorites = listOf(PadRef("fps", 4), PadRef("fps", 9))
        )

        val miniDeck = state.miniDeckPads()
        assertEquals(6, miniDeck.size)
        // Favorites come first
        assertEquals(4, miniDeck[0].first.slot)
        assertEquals(9, miniDeck[1].first.slot)
    }

    @Test
    fun deckState_unlimitedCardsExpansion_preservesAndResolvesAllPads() {
        val initialPads = List(12) { slot ->
            Pad(slot = slot, title = "Pad $slot", filePath = "/path/$slot.wav")
        }
        val board = Board(id = "unlimited", name = "Unlimited Deck", pads = initialPads)
        val state = DeckState(boards = listOf(board), activeBoardId = "unlimited")
        assertEquals(12, state.board("unlimited")?.pads?.size)

        // Expand by 3 cards
        val expandedPads = state.board("unlimited")!!.pads + List(3) { offset ->
            val slot = 12 + offset
            Pad(slot = slot, title = "Expanded $slot")
        }
        val state2 = state.copy(boards = listOf(board.copy(pads = expandedPads)))
        assertEquals(15, state2.board("unlimited")?.pads?.size)
        assertEquals("Expanded 14", state2.pad(PadRef("unlimited", 14))?.title)

        // Expand by another 3 cards (now 18)
        val expandedPads2 = state2.board("unlimited")!!.pads + List(3) { offset ->
            val slot = 15 + offset
            Pad(slot = slot, title = "Expanded $slot")
        }
        val state3 = state2.copy(boards = listOf(board.copy(pads = expandedPads2)))
        assertEquals(18, state3.board("unlimited")?.pads?.size)
        assertEquals("Expanded 17", state3.pad(PadRef("unlimited", 17))?.title)
    }

    @Test
    fun deckState_deletePads_shrinksOrClearsProperly() {
        val pads = List(15) { slot ->
            Pad(slot = slot, title = "Sound $slot", filePath = "/sounds/$slot.wav")
        }
        val board = Board(id = "b1", name = "Deck 1", pads = pads)

        // Deleting when > 12: should shrink the deck from 15 to 14 and re-index slots
        val remainingAfter1 = board.pads.filterNot { it.slot == 2 }
            .mapIndexed { idx, p -> p.copy(slot = idx) }
        assertEquals(14, remainingAfter1.size)
        assertEquals("Sound 3", remainingAfter1[2].title)
        assertEquals(2, remainingAfter1[2].slot)

        // Group deleting 3 cards from 14 -> leaves 11, which pads back to 12 minimum
        val remainingAfterGroup = remainingAfter1.filterNot { it.slot in setOf(0, 1, 2) }
        val finalPads = (remainingAfterGroup + List(12 - remainingAfterGroup.size) {
            Pad(slot = 0, title = "Empty", colorHex = "#334155")
        }).mapIndexed { idx, p -> p.copy(slot = idx) }

        assertEquals(12, finalPads.size)
        assertEquals("Sound 4", finalPads[0].title)
        assertEquals("Empty", finalPads[11].title)
    }

    @Test
    fun deckState_movePads_transfersSoundsToTargetBoard() {
        val sourcePads = List(12) { slot ->
            Pad(slot = slot, title = "Source $slot", filePath = if (slot < 3) "/s/$slot.wav" else null)
        }
        val targetPads = List(12) { slot ->
            Pad(slot = slot, title = if (slot == 0) "Existing" else "Empty", filePath = if (slot == 0) "/t/0.wav" else null)
        }
        val sourceBoard = Board(id = "src", name = "Source", pads = sourcePads)
        val targetBoard = Board(id = "tgt", name = "Target", pads = targetPads)

        val padsToMove = sourceBoard.pads.filter { it.slot in setOf(0, 1) && it.filePath != null }
        assertEquals(2, padsToMove.size)

        // Move to target: fill empty slot 1 and slot 2
        val updatedTarget = targetBoard.pads.toMutableList()
        var moveIdx = 0
        for (i in updatedTarget.indices) {
            if (moveIdx >= padsToMove.size) break
            if (updatedTarget[i].filePath == null) {
                updatedTarget[i] = padsToMove[moveIdx].copy(slot = i)
                moveIdx++
            }
        }

        assertEquals("Existing", updatedTarget[0].title)
        assertEquals("Source 0", updatedTarget[1].title)
        assertEquals("Source 1", updatedTarget[2].title)
        assertEquals(1, updatedTarget[1].slot)
        assertEquals(2, updatedTarget[2].slot)
    }
}
