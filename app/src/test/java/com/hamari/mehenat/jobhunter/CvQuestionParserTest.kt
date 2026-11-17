package com.hamari.mehenat.jobhunter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CvQuestionParserTest {
    @Test fun parsesJsonArrayWrappedInModelMarkdown() {
        val questions = CvQuestionParser.parse("Here you go:\n```json\n[\"Which tools have you used?\",\"How many years of experience do you have?\"]\n```")
        assertEquals(2, questions.size)
        assertEquals("Which tools have you used?", questions[0])
    }

    @Test fun rejectsMalformedAndOverlongQuestions() {
        assertTrue(CvQuestionParser.parse("not json").isEmpty())
        assertTrue(CvQuestionParser.parse("[\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"]").isEmpty())
    }

    @Test fun limitsQuestionCountAndRemovesDuplicates() {
        val result = CvQuestionParser.parse("[\"Question one?\",\"Question one?\",\"Question two?\",\"Question three?\",\"Question four?\",\"Question five?\",\"Question six?\"]")
        assertEquals(5, result.size)
    }
}
