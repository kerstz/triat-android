package com.ivor.ivormusic.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The gate on the YouTube session a desktop shares, on invented cookies. */
class SharedSessionTest {

    private val valid = "SID=abc; HSID=def; SAPISID=ghi; __Secure-3PAPISID=jkl"

    @Test
    fun `a complete session passes`() {
        assertNull(sharedSessionProblem(valid))
        assertNull(sharedSessionProblem("SID=a; __Secure-3PAPISID=b"))
    }

    @Test
    fun `a session over 64 KiB is refused, exactly at the limit it passes`() {
        val filler = "; X=" + "a".repeat(MAX_SHARED_SESSION_CHARS)
        assertEquals(PROBLEM_TOO_LARGE, sharedSessionProblem(valid + filler))
        val room = MAX_SHARED_SESSION_CHARS - valid.length - "; X=".length
        assertNull(sharedSessionProblem(valid + "; X=" + "a".repeat(room)))
        assertEquals(PROBLEM_TOO_LARGE, sharedSessionProblem(valid + "; X=" + "a".repeat(room + 1)))
    }

    @Test
    fun `a newline or any control character is refused`() {
        assertEquals(PROBLEM_CONTROL_CHARACTERS, sharedSessionProblem("$valid\nSet-Cookie: x=y"))
        assertEquals(PROBLEM_CONTROL_CHARACTERS, sharedSessionProblem("$valid\r\nX: y"))
        assertEquals(PROBLEM_CONTROL_CHARACTERS, sharedSessionProblem("SID=a;\tSAPISID=b"))
        assertEquals(PROBLEM_CONTROL_CHARACTERS, sharedSessionProblem("$valid\u0000"))
        assertEquals(PROBLEM_CONTROL_CHARACTERS, sharedSessionProblem("$valid\u007f"))
    }

    @Test
    fun `a session without SAPISID or without SID is refused`() {
        assertEquals(PROBLEM_INCOMPLETE, sharedSessionProblem("SID=abc; HSID=def"))
        assertEquals(PROBLEM_INCOMPLETE, sharedSessionProblem("SAPISID=ghi; HSID=def"))
        assertEquals(PROBLEM_INCOMPLETE, sharedSessionProblem("foo=bar"))
        assertEquals(PROBLEM_INCOMPLETE, sharedSessionProblem(""))
    }
}
