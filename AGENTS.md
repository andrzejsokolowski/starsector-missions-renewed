# Missions - Renewed

These instructions apply to this repository, including when it is opened without the parent mod workspace.
The workspace `AGENTS.md` one folder up still governs releases, packaging, Git identity and communication.

## Notion

Tasks, history, test lists and the design Q and A: the **Missions - Renewed board** in Andrzej's Notion,
under **Starsector mods** (`collection://eafd287a-43e0-476f-8bf6-7fffa33046cc`). Ticket ids start with MSR.
Read the board with `notion-query-data-sources` mode `view` on `view://41320ca7-5d20-812c-a12a-000cd4d2fc7d`.
Working rules and game API notes: **Claude Information** (`collection://7bf9b1d6-b9e5-4b62-87f1-757bbaa51919`),
starting with "Start here: working on Starsector mods through Notion". Update the board during the work.

## Build

`./gradlew releaseZip` compiles against the game and LunaLib (`gradle.properties` names the game folder),
runs the checks and writes `MissionsRenewed.zip` in this folder. Mod id `missions_renewed`, package
`missionsrenewed`, staging folder `MissionsRenewed`. Plain Java 17, no Kotlin.

## Layout

- `data/config/missions_renewed/tiers.json` - tiers 0 to 7: credit reward, hostile fleet points, cargo
  units and investment stake as [min, max], plus a pick weight. `TierTable` reads it with
  `getMergedJSON`, so other mods can add to it. The build checks 0 to 7 are present and every range is sane.
- `src/missionsrenewed/bar/MissionBoardBarEvent` - the one bar event. One instance lives in
  `PortsideBarData` for good (`isAlwaysShow`, never pinned by `wasShownAtMarket`). Each market rolls
  `$missions_renewed_offer` in its memory for 20 to 40 days ("none" or "<tier>|<FAMILY>"), seeded by
  `BarEventManager.getSeed` so a reload does not re-roll. BarCMD caps the events per visit but does not
  count always-show ones, which is why this is not a normal bar event creator.
- `MissionsRenewedModPlugin` - loads the tier table at startup, keeps exactly one board event per save,
  and on the first load of an existing save clears every market's roll and `$BarCMD_shownEvents` (BarCMD
  ignores events missing from that list until it expires, up to 40 days).
- `src/missionsrenewed/missions/MissionFamily` - combat, hauling, investment, contribution, expedition.
  No mission content yet: accepting an offer only shows its tier and family.
- `src/missionsrenewed/Settings` - LunaLib reads with defaults.

## Known before the first release

- The board event is a class in the save, so removing the mod mid-game breaks that save. Decide whether
  that is acceptable before release (most content mods have the same limit) and say so in the forum post.
