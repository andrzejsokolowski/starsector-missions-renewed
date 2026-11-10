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

- `data/config/missions_renewed/tiers.json` - tiers 0 to 7: credit reward, hostile fleet size in deployment points (`deploymentPoints`; fleets the game generates by fleet points are trimmed to it), cargo
  units, fuel units, crew and investment stake as [min, max], plus a pick weight. `TierTable` reads it with
  `getMergedJSON`, so other mods can add to it. The build checks 0 to 7 are present and every range is sane.
- `src/missionsrenewed/bar/MissionBoardBarEvent` - the one bar event. One instance lives in
  `PortsideBarData` for good (`isAlwaysShow`, never pinned by `wasShownAtMarket`). Each market rolls
  `$missions_renewed_offer` in its memory for 20 to 40 days ("none" or "<tier>|<FAMILY>"), seeded by
  `BarEventManager.getSeed` so a reload does not re-roll. BarCMD caps the events per visit but does not
  count always-show ones, which is why this is not a normal bar event creator.
- `MissionsRenewedModPlugin` - loads the tier table at startup, keeps exactly one board event per save,
  and on the first load of an existing save clears every market's roll and `$BarCMD_shownEvents` (BarCMD
  ignores events missing from that list until it expires, up to 40 days).
- `src/missionsrenewed/tiers/PlayerTier` - places the player on the table (best of fleet DP vs
  deploymentPoints min, colony income per month vs reward min, credits vs stake min). The roll multiplies that
  tier's weight by 1 + lean and its neighbours by 1 + lean/2 (LunaLib `mr_playerLean`, default 100).
- `src/missionsrenewed/missions/MissionFamily` - combat, hauling, fuel run, crew transport, marines,
  investment, contribution, expedition. The bar option shows "(tier N, family)". Only families with
  `playable` true are rolled (hauling, fuel run, crew transport, contribution, investment). `Contracts`
  maps a type to its builder and says whether a market can offer it. The design answers that govern
  missions are on ticket MSR-1.
- `src/missionsrenewed/missions/ContractIntel` - base for every signed contract: advance on signing,
  payment on success, penalty on failure or abandon (taken even into debt), reputation with the client's
  faction, deadline, at most `MAX_OPEN` (3) at once, Abandon button in the intel screen. Each type also
  writes its own bar offer (`addOfferText`, `addOfferTerms`, `cannotSignReason`), shown by `bar/OfferScreen`.
- `src/missionsrenewed/missions/delivery/` - `DeliveryContract` (bring N units to a destination; docking
  there with all of it completes the job) and its types: `hauling/HaulingContract` (kept in the old
  package and with the old field names, because 0.1.0 saves hold it), `FuelRunContract` (fuel tanks),
  `CrewTransportContract` (crew quarters) and `ContributionContract` (the player finds the goods, or one
  colony item at tiers 4 to 6). `DeliveryContracts.build` picks a destination (size 3+, not hostile to the
  player or the client, 3 to 10 + 4 x tier light years). Handed-over goods stay under 60 percent of the
  pay, and the penalty is at least advance + goods value. Deadline: twice the trip at burn 8 plus 20 days.
  `DeliveryListener` (transient, added every load) completes jobs on docking.
- `src/missionsrenewed/missions/investment/` - stake on signing (negative advance), payout after 5 to 12
  months, half the stake back on early withdrawal, stake refunded if the market dies. No pirates or Pathers.
- `src/missionsrenewed/missions/combat/` - `CombatContract` (a fleet spawned on signing near a planet,
  preferably in an uncolonised system; done when the player fights it and it is destroyed or down to a
  quarter of its fleet points; void if someone else kills it). `EnemyFleets` picks and builds enemies:
  tiers 0 to 4 pirates or Pathers from FleetFactoryV3, tiers 5 to 7 a group from
  `data/config/missions_renewed/enemy_fleets.json` (hand-built from variants with no inflater, AI cores on
  automated ships only where `aiCores` is true, plus the game's ThreatFIDConfig or Dweller drop groups as
  its own fleets have them; or generated from a modded faction, `aiCores` then sets FleetParamsV3.aiCores;
  groups whose faction is missing are skipped). Every contract fleet ends with GO_TO_LOCATION_AND_DESPAWN.
- Both bar events reset BaseBarEvent's transient `done` and `noContinue` in init: the board is one instance
  for the whole session, and BarEventDialogPlugin ends the event right after init when `done` is still set.
  Offers are built through `Contracts.safeBuild`, so a builder that throws gives no offer instead of a crash.
- `src/missionsrenewed/missions/marines/` - `RaidContract` (disrupt a named industry at a market hostile to
  the client, picked by `MarketCMD.getDefenderStr` inside the tier's `marines` range; `RaidListener`,
  transient, reports raids) and `RaidContracts` (raid or `delivery/GarrisonContract`, the player's marines
  to a same-faction market).
- `src/missionsrenewed/missions/expedition/` - cargo to a planet in an uncolonised system (`Places`), delivered
  by flying within 300 units. The ambush is rolled at offer time from a shown chance and spawned when the
  player enters the system.
- `delivery/SmugglingContract` - goods illegal at the destination. Vanilla patrol scans do the rest.
- Combat targets and ambushes are anonymous (`EnemyFleets.ANONYMOUS_NAME`, offers say only where someone was
  attacked): Andrzej wants the enemy to be a surprise. Never name Omega, Threat, Dwellers or modded enemies in
  offers, intel, LunaLib text, README or the forum post.
- Completed contracts offer the client as a potential contact (`ContactIntel.addPotentialContact`).
- LunaLib `mr_job_<type>` (lower-case enum name) switches each type, `mr_minTier` / `mr_maxTier` bound the
  roll (swapped when crossed). `liveOffer` hides rolled offers that no longer fit, the roll skips them.
- Test mode (LunaLib `mr_testMode`, in the Debug section, ignores the job and tier settings): the board event adds an option carrying a fresh
  `TestContractBarEvent` (BarCMD runs any bar event that is an option's data, so it is never registered).
  It builds any type and tier on the spot and ignores the open contract limit. Intel gets Complete now and
  Fail now buttons.
- `DeliveryListener` also puts the board event on `$BarCMD_shownEvents` when a market is opened: BarCMD
  ignores events missing from that list until it expires, which hid new offers and the test option.
- The bar keeps the unsigned contract in market memory (`$missions_renewed_contract`) for the rest of the
  offer's time, so looking twice shows the same terms.
- `src/missionsrenewed/Settings` - LunaLib reads with defaults.

## Known limits

- The board event and the contracts are classes in the save, so removing the mod mid-game breaks that
  save. The README and forum post say so.
- `forum-post.txt` is the forum draft. No topic exists yet.
