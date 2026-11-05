package com.hamari.mehenat.jobhunter

import com.hamari.mehenat.jobhunter.ai.AgentPromptBuilder
import com.hamari.mehenat.jobhunter.ai.PersonalAgent
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptBuilderTest {
    @Test fun promptRequiresEvidenceAndForbidsFabrication() {
        val prompt = AgentPromptBuilder.build(
            PersonalAgent.CV_TAILOR,
            "Tailor this CV for the role.",
            verifiedProfile = "Worked as a warehouse assistant for 6 months.",
            vacancyOrSourceEvidence = "Forklift license preferred."
        )
        assertTrue(prompt.contains("never add qualifications", ignoreCase = true))
        assertTrue(prompt.contains("Worked as a warehouse assistant"))
        assertTrue(prompt.contains("Forklift license preferred"))
        assertTrue(prompt.contains("Not provided"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyTask() {
        AgentPromptBuilder.build(PersonalAgent.CV_ANALYST, " ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedProfileContext() {
        AgentPromptBuilder.build(
            PersonalAgent.CV_ANALYST,
            "Review CV",
            verifiedProfile = "x".repeat(5001)
        )
    }

    @Test fun visaAgentRequiresOfficialEvidence() {
        val prompt = AgentPromptBuilder.build(PersonalAgent.VISA_EVIDENCE, "Can I work in Australia?")
        assertTrue(prompt.contains("If no official evidence is supplied"))
        assertTrue(prompt.contains("Not provided."))
    }
}
