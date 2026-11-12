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
- `src/missionsrenewed/bar/MissionBoardBarEvent` - the one bar event, the mission board (0.7.0, MSR-24). One
  instance lives in `PortsideBarData` for good (`isAlwaysShow`, never pinned by `wasShownAtMarket`). The bar
  shows one option, "Open the mission board". Each market rolls `$missions_renewed_offer` in its memory for
  20 to 40 days: "none", or 1 to 5 lines joined by ';' (`lineCount` by market size, Andrzej's table on MSR-24: size 3 rarely 1, 4: 1-2, 5: 1-2 mostly 2, 6: 2-3, 7: 3-4, 8: 4-5, 9+: 5, size 2 and below none), each "<tier>|<FAMILY>"
  with its own tier roll, or "-" once signed. A pre-0.7.0 single line reads as a board of one. Seeded by
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
  player or the client, 3 to 10 + 4 x tier light years). Hauling, fuel runs and crew transport pay a third of the rolled reward (`EASY_PAY_SHARE`);
  the cargo cap still uses the full reward. Handed-over goods stay under 60 percent of the
  pay, and the penalty is at least advance + goods value. Deadline: twice the trip at burn 8 plus 20 days.
  `DeliveryListener` (transient, added every load) completes jobs on docking.
- `src/missionsrenewed/missions/investment/` - stake on signing (negative advance), payout after 5 to 12
  months, stake refunded if the market dies. No pirates or Pathers. Since 0.8.0 (MSR-7) no withdrawal
  (`canAbandon` false) and a hidden `failChance` by risk: guaranteed 0, safe 5-15 percent (fails return half
  the stake), risky 30-50 percent (fails lose all); profit x0.6 / x1 / x2.2. The client says the risk in words.
- `src/missionsrenewed/missions/combat/` - `CombatContract` (a fleet spawned on signing near a planet,
  preferably in an uncolonised system; done when the player fights it and it is destroyed or down to a
  quarter of its fleet points; void if someone else kills it). `EnemyFleets` picks and builds enemies:
  tiers 0 to 4 pirates or Pathers from FleetFactoryV3. Tiers 5 to 7 (0.8.1, Andrzej: the strange enemies
  "pretty much never attack anything"): an elite FleetFactoryV3 war fleet of a faction hostile to the client
  (`warFleetFactions`: shown in the intel tab, has ships, not pirates, Pathers, Omega, Threat, Dwellers,
  Remnants, derelicts or a group's faction; pirates and Pathers only when no other faction is hostile), sharing
  `HOSTILE_FACTION_WEIGHT` 60 against the groups' own weights in
  `data/config/missions_renewed/enemy_fleets.json` (Threat and Dwellers 1.5, Omega 1 and tier 7 only with at
  most one Tesseract and half the DP via `limits` and `strength`, modded groups 5). Groups are hand-built from variants with no inflater, AI cores on
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
- `delivery/SmugglingContract` - dropped in 0.4.6 (MSR-12): `MissionFamily.SMUGGLING` is not playable and nothing
  builds one. The class and the enum value stay so saves with an open contract or a rolled offer still load.
- Combat pay: `CombatContracts.PAY_SHARE` per tier (1, 1, 1, 1, 0.75, 0.6, 0.5, 0.4) of the tier reward.
- Combat targets and ambushes are anonymous (`EnemyFleets.ANONYMOUS_NAME`, offers say only where someone was
  attacked): Andrzej wants the enemy to be a surprise. Never name Omega, Threat, Dwellers or modded enemies in
  offers, intel, LunaLib text, README or the forum post.
- 0.6.0 job types:
  - `defense/` - `DefenseContract` (0.8.0): the fleet spawns on signing 2500 units out from a same-faction market
    (`EnemyFleets.placeGathering`: HOLD, ignored by and ignoring other fleets) and attacks (`startAttack`,
    ATTACK_LOCATION) when the player enters the system. Beaten with the player in the fight = done, beaten
    without the player = void, still there at the deadline (round trip + 30 days) = failed. `pickEnemy(...,
    mustBeHostile)` keeps the attackers hostile to the market so its station fights.
  - `lead/ShipLeadContract`: negative advance (the price of the lead), no pay or penalty. A pristine derelict
    (`BaseThemeGenerator.addSalvageEntity` WRECK + `ShipRecoverySpecialData`, guards from tier 3) or a pirate or
    Pather flagship tagged `Tags.VARIANT_ALWAYS_RECOVERABLE`. The famous ship comes from `Hulls.pickFamous`
    (0.8.1): warships only, no pirate or Path refits from tier 4, weighted by hull value to the power tier/4.
    `ships/Hulls` picks stock variants by the tier's DP
    band (`Hulls.DP_BAND`), skipping stations, modules, unboardable, hidden, civilian, automated and D-hulls.
  - `ships/ShipWantedContract` (0.8.0: a client wants one named ship from the player's fleet in the tier's DP band,
    never the flagship; pays hull value x (1.3 + 0.05 x tier) x Starship Legends fame on signing) and
    `ShipSaleContract` (client sells the player a ship; completes on signing; since 0.8.1 pristine only 15
    percent of the time, otherwise 1 to 4 D-mods from `conditionSeed` and 7 percent of hull value off per D-mod). `ShipDealContracts` picks
    one of the two. `ShipOrderContract` + `bar/HandOverBarEvent` (0.6.0 hull orders) stay only for old saves.
  - `compat/Legends` reads Starship Legends fame (`RepRecord.getTier().ordinal()`, 0 to 5) through
    `LegendsBridge`, the only class that names its types, compiled against its jar (`compileOnly`) and only
    called when the mod is enabled.
  - Offers that are about one ship show it on the right (`ContractIntel.showOfferVisual`, the game's
    `VisualPanelAPI.showFleetMemberInfo(member, true)` as vanilla's SurplusShipHull does). Combat and defense
    offers build their fleet from the seed at offer time and show a glimpse (`combat/FleetPreview`,
    `addShipList` like PersonBountyIntel); signing spawns that same fleet.
  - `conquest/` - Nexerelin only (`ConquestContracts.canOffer`), `MissionFamily.minTier` 5; market size 3 / 4 / 5
    at tier 5 / 6 / 7 (MSR-22). Never story markets (`isStoryMarket`: a list of core ids, `Misc.isStoryCritical`,
    `Tags.NOT_RANDOM_MISSION_TARGET`). Done when the market passes to the client's faction, the player's faction
    or the player's commission faction.
  - `lead/SalvageLeadContract`: `Misc.addDebrisField` with BASIC loot worth half the tier reward and extra
    drop-group rolls by tier (`EXTRA_DROPS`, raised in 0.8.0); reaching the field completes the lead, the loot is
    the game's own salvage screen. A scavenger fleet guards it (`EnemyFleets.scavengers`: scavenger faction,
    quality 0, no officers, a quarter of the tier's DP).
  - `MissionFamily.minTier` + `Contracts.canOffer(family, market, tier)`: the bar rolls the tier first, then the
    types offered at it. Test mode greys out tiers below a type's minimum.
- Completed contracts offer the client as a potential contact (`ContactIntel.addPotentialContact`).
- Proving yourself (LunaLib `mr_proving`, default on, `mr_provingCount` default 3, MSR-25): `tiers/Proving` counts
  completed contracts per tier in sector memory (`$missions_renewed_done_tier_<n>`); the board offers up to one
  tier above the highest tier with enough completions at or above it. `countsAsProof` is false for ship sales,
  wanted ships and investments. Test mode ignores it.
- Offer text is the client talking, in character (Andrzej, 2026-11-12): no narrator lines that explain the
  rules ("the breach clause applies"), no exact odds the client could not know. The terms box under it stays
  plain.
- LunaLib `mr_job_<type>` (lower-case enum name) switches each type, `mr_minTier` / `mr_maxTier` bound the
  roll (swapped when crossed). `liveOffer` hides rolled offers that no longer fit, the roll skips them.
- Test mode (LunaLib `mr_testMode`, in the Debug section, ignores the job and tier settings): the board adds an option that runs a fresh
  `TestContractBarEvent` as a sub-screen (the board forwards `optionSelected` to it and shows the board again
  when it sets `done`; `HandOverBarEvent` runs the same way, so neither is ever registered).
  It builds any type and tier on the spot and ignores the open contract limit. Intel gets Complete now and
  Fail now buttons.
- `DeliveryListener` also puts the board event on `$BarCMD_shownEvents` when a market is opened: BarCMD
  ignores events missing from that list until it expires, which hid new offers and the test option.
- The bar keeps each line's unsigned contract in market memory (`$missions_renewed_contract` for line 0,
  that key + the line number for the others) for the rest of the board's time, so looking twice shows the same
  terms. Signing marks only that line taken.
- `src/missionsrenewed/Settings` - LunaLib reads with defaults. `payMult(family)` (`mr_pay_<type>`) scales the money of
  one job type on top of `rewardMult`; builders keep the rolled reward for cargo, goods and marine amounts and use
  the multiplied pay only for advance, payment and penalty.

## Known limits

- The board event and the contracts are classes in the save, so removing the mod mid-game breaks that
  save. The README and forum post say so.
- `forum-post.txt` is the text of the first post of the forum topic https://fractalsoftworks.com/forum/index.php?topic=36287
  (modThreadId 36287 in both metadata files).
