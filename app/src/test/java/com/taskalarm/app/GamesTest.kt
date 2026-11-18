package com.taskalarm.app

import org.junit.Assert.*
import org.junit.Test

class GamesTest {
  @Test fun breakoutCountsOnlyActivePlayAndResetsAfterIdling() {
    val game = BreakoutSim("easy", targetMs = 10_000, idleLimitMs = 20_000)
    var now = 1_000L
    game.step(now)
    repeat(100) { now += 33; game.step(now) }
    assertEquals("no input, no credit", 0, game.playedMs)
    var x = 0.3f
    repeat(400) { now += 33; x = if (x > 0.6f) 0.3f else x + 0.01f; game.input(x, now); game.step(now) }
    assertTrue(game.complete)
    val partial = BreakoutSim("hard", targetMs = 60_000)
    repeat(100) { now += 33; partial.input(0.3f + (it % 2) * 0.1f, now); partial.step(now) }
    assertTrue(partial.playedMs > 2_000)
    now += 21_000; partial.step(now)
    assertEquals("idle reset", 0, partial.playedMs)
    assertEquals(1, partial.resets)
  }

  @Test fun breakoutGivesNoCreditForLongFrameGapsOrAfterPause() {
    val game = BreakoutSim("medium", targetMs = 60_000)
    var now = 0L
    game.step(now)
    now += 33; game.input(0.2f, now); game.step(now)
    val before = game.playedMs
    now += 1_000; game.input(0.4f, now); game.step(now)
    assertEquals("a 1 s frame gap earns nothing", before, game.playedMs)
    game.pause(); now += 500; game.input(0.6f, now); game.step(now)
    assertEquals(before, game.playedMs)
  }

  @Test fun breakoutKeepsTheBallInsideAndRespawnsLostBalls() {
    val game = BreakoutSim("hard", targetMs = 60_000)
    var now = 0L
    repeat(3_000) { now += 16; game.step(now); assertTrue(game.ballX in 0f..1f); assertTrue(game.ballY > -0.01f && game.ballY < 1.1f) }
    assertTrue(game.bricks.size in 1..24)
  }

  @Test fun mazeRequiresCheckpointsInOrderAndWallsBlockTheBall() {
    val maze = MazeSim("medium", flip = false)
    assertEquals(3, maze.checkpoints.size)
    // Push straight down from the start: the first wall (gap on the right) stops the ball.
    repeat(500) { maze.step(0f, 1f, 16) }
    assertTrue(maze.y < maze.walls[0].y)
    assertEquals(0, maze.reached)
    // Navigate: right to the gap, down past the wall, then follow the checkpoints.
    fun drive(target: Spot) { repeat(4_000) { if (maze.reached >= maze.checkpoints.indexOf(target) + 1) return
      maze.step(Math.signum(target.x - maze.x), Math.signum(target.y - maze.y), 16) } }
    repeat(300) { maze.step(1f, 0f, 16) }
    drive(maze.checkpoints[0])
    assertEquals(1, maze.reached)
    repeat(300) { maze.step(-1f, 0f, 16) }
    drive(maze.checkpoints[1])
    drive(maze.checkpoints[2])
    assertTrue(maze.complete)
  }

  @Test fun mazeIgnoresStaleFramesAndNaNInput() {
    val maze = MazeSim("easy", flip = true)
    val start = maze.x to maze.y
    maze.step(1f, 1f, 5_000); maze.step(Float.NaN, Float.NaN, 16)
    assertEquals(start, maze.x to maze.y)
  }

  @Test fun lightHoldSurvivesSilenceAndResetsWhenItDarkens() {
    val hold = LightHold(200f, 3_000)
    hold.sample(250f, 1_000)
    hold.advance(2_500); assertFalse(hold.complete)
    hold.advance(4_000); assertTrue("steady light sends no readings but still counts", hold.complete)
    val dim = LightHold(200f, 3_000)
    dim.sample(300f, 0); dim.advance(2_000); dim.sample(120f, 2_100); dim.advance(5_000)
    assertEquals(0, dim.heldMs)
    dim.sample(Float.NaN, 5_100); assertEquals(120f, dim.lux)
  }

  @Test fun stepTrackerRejectsOldOrReplayedEventsAndCapsCounterJumps() {
    val second = 1_000_000_000L
    val steps = StepTracker(startNanos = 10 * second)
    steps.detector(9 * second, 10 * second) // before start
    steps.detector(11 * second, 14 * second) // older than 2 s
    steps.detector(12 * second, 12 * second); steps.detector(12 * second, 12 * second) // replay
    assertEquals(1, steps.steps)
    steps.counter(1_000f, 13 * second, 13 * second) // baseline only
    steps.counter(1_005f, 14 * second, 14 * second)
    steps.counter(1_500f, 15 * second, 15 * second) // jump: re-baseline, no credit
    steps.counter(1_502f, 16 * second, 16 * second)
    assertEquals(8, steps.steps)
  }
}
