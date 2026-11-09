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
- `src/missionsrenewed/tiers/PlayerTier` - places the player on the table (best of fleet points vs
  fleetPoints min, colony income per month vs reward min, credits vs stake min). The roll multiplies that
  tier's weight by 1 + lean and its neighbours by 1 + lean/2 (LunaLib `mr_playerLean`, default 100).
- `src/missionsrenewed/missions/MissionFamily` - combat, hauling, fuel run, crew transport, marines,
  investment, contribution, expedition. The bar option shows "(tier N, family)". Only families with
  `playable` true are rolled (hauling so far). The design answers that govern missions are on ticket MSR-1.
- `src/missionsrenewed/missions/ContractIntel` - base for every signed contract: advance on signing,
  payment on success, penalty on failure or abandon (taken even into debt), reputation with the client's
  faction, deadline, at most `MAX_OPEN` (3) at once, Abandon button in the intel screen.
- `src/missionsrenewed/missions/hauling/` - `HaulingContracts.build` picks a destination (size 3+, not
  hostile to the player or the client, 3 to 10 + 4 x tier light years), a good legal at both ends whose
  base value stays under 60 percent of the pay, and a deadline of twice the trip at burn 8 plus 20 days.
  Penalty = max(half the pay, advance + cargo value). `HaulingDelivery` (transient listener, added every
  load) completes the job when the player docks at the destination with all the cargo.
- The bar keeps the unsigned contract in market memory (`$missions_renewed_contract`) for the rest of the
  offer's time, so looking twice shows the same terms.
- `src/missionsrenewed/Settings` - LunaLib reads with defaults.

## Known limits

- The board event and the contracts are classes in the save, so removing the mod mid-game breaks that
  save. The README and forum post say so.
- `forum-post.txt` is the forum draft. No topic exists yet.
