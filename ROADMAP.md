# Spewboard roadmap and proposals

This is the source of truth for what Spewboard does and where it's going. **Calum owns the
product and makes every call here.** Everyone else (Bryan, Steve, AJ and their Claude Code
sessions) suggests; nothing a user would notice gets built until Calum has approved it below.

## How it works

1. **Got an idea, a concern or some feedback that would change the app?** Add a row to
   [Proposals](#proposals) with status **Proposed**: what, why, who and when. One idea per row.
   Bug fixes to things that already exist don't need a row: open an issue as usual.
2. **Calum decides.** He sets the status to **Approved**, **Declined** or **Discussing** and adds a
   note. Only Calum changes a status. If he decides somewhere else (WhatsApp, a call), whoever
   heard it can record it, saying where: "Calum, WhatsApp, 3 Nov".
3. **Approved means someone can build it.** Open an issue for it, put the issue number in the row,
   and work through a PR as usual. When it's released, set the status to **Done** with the PR.
4. **Declined rows stay**, with Calum's reason, so the same idea doesn't keep coming back.

Calum can edit this file straight on GitHub (the pencil icon), or just tell someone his decision.

## Direction

_Calum: a few lines here on what Spewboard is for, who it's for and what matters most right now.
Everything below gets judged against it._

## Proposals

| # | Proposal and why | From | Date | Status | Calum's decision and notes | Issue / PR |
|---|---|---|---|---|---|---|
| R1 | **Take the celebrity names out of the README.** The in-app presets are already generic; the README still named real people and characters, which is a risk once the app earns money. | AJ | 1 Nov 2026 | Done | Done by Calum. | c2bd7b6 |
| R2 | **Add a single "Pro" switch in the code**, always on for now, so paid features can be added later without touching every screen. | AJ | 1 Nov 2026 | Done | Done by Calum (`ProConfig`). | c2bd7b6 |
| R3 | **Privacy policy.** Needed for Google Play, and the feedback button already sends data. | AJ | 1 Nov 2026 | Done | Done by Calum (`PRIVACY_POLICY.md`). | c2bd7b6 |
| R4 | **Written ownership note** covering who owns the code and how any revenue is shared, agreed before any money comes in. | AJ | 1 Nov 2026 | Done | Ownership written by Calum (`OWNERSHIP.md`). Revenue and costs to be agreed in that file before paid features or ads. | c2bd7b6 |
| R5 | **Sounds we have the rights to.** Search pulls clips from MyInstants, mostly copyrighted film, TV and meme audio. Fine while free; a risk with ads or a paid unlock. Idea: a starter pack of free-licence or home-made sounds, with web search as "import at your own risk". | AJ | 1 Nov 2026 | Proposed | | |
| R6 | **Google Play developer account in Calum's name.** New accounts need a 14-day closed test with 12 testers before going public, so starting early saves time. | AJ | 1 Nov 2026 | Proposed | | |
| R7 | **A release signing key for Google Play**, separate from the shared test key. | AJ | 1 Nov 2026 | Proposed | | |
| R8 | **Choose a licence for the code, and whether the repo stays public** once there are paid features (anyone can build a copy with the Pro lock removed). | AJ | 1 Nov 2026 | Proposed | | |
| R9 | **Keep playing sounds unlimited** and charge for extras (custom uploads, more decks, game profiles) instead of a 20 plays a day cap. A soundboard gets spammed, so 20 plays is about ten minutes of chat. | AJ | 1 Nov 2026 | Proposed | | |
| R10 | **If there are ads, keep them out of voice chat:** mute ads completely, or show none while the cable is plugged in. The cable is the headphone socket, so ad audio would go straight into the chat. | AJ | 1 Nov 2026 | Proposed | | |
| R11 | **Share the original MP3 instead of the converted WAV.** The WAV is about ten times bigger, and WhatsApp may send it as a document rather than a playable clip (needs a check on a real phone). | AJ | 1 Nov 2026 | Proposed | | |
| R12 | **Wording and copy audit** across the app. | Steve | Nov 2026 | Proposed | | #3 |

## Statuses

- **Proposed**: waiting for Calum.
- **Discussing**: Calum wants to talk it through or needs more information.
- **Approved**: go ahead; open an issue and build it.
- **Declined**: not doing it. The reason stays in the row.
- **Done**: released. The row links the PR or commit.
