# Missions - Renewed

A Starsector 0.98a mod by Oddisz. Bar missions sorted into tiers 0 to 7, from pocket money to jobs worth
millions, so contracts stay worth taking once your colonies earn a fortune.

Requires LunaLib.

## How it works

- Every bar has a chance (50 percent by default) to have a mission board. "Open the mission board" lists
  1 to 5 contracts by market size, each with its own tier and job type, for example "Tier 5 hauling":
  1 or 2 at size 4 and 5, 2 or 3 at size 6, 3 or 4 at size 7, 4 or 5 at size 8, always 5 at size 9 and up.
  Size 3 markets rarely have a board, with 1 contract. Smaller markets have none. Ship hand-overs are on the board too.
- Tiers are random, but lean a little toward your fleet size, colony income and credits.
- Each contract pays an advance when you sign and the rest on completion. Missing the deadline or
  abandoning the contract charges a penalty, even if that leaves you in debt, and costs reputation with
  the client's faction.
- You can hold 3 contracts at once. Open contracts are in the intel screen under Missions, with an
  Abandon button.

## Job types

- **Hauling**: the client hands you the cargo. Deliver all of it to the named market before the
  deadline. Docking there completes the job. Running off with the cargo costs more than it is worth.
- **Fuel run**: the client fills your fuel tanks. Arrive with the full amount: every jump on the way
  burns into the delivery unless you bring spare fuel. Needs free fuel tank space.
- **Crew transport**: contract workers ride in your crew quarters. Crew lost in battle count against the
  delivery. Needs free crew quarters.
- **Contribution**: bring goods you find or buy yourself to a construction project at another market.
  The pay covers the goods about twice over. At tiers 4 to 6 the project may ask for a colony item.
- **Investment**: put up a stake, get it back with profit after 5 to 12 months. No pulling out. The client
  calls the venture guaranteed, safe or risky: safe ones rarely fail and return half the stake when they
  do, risky ones pay much more but can lose all of it. Pirates and the Luddic Path do not offer them.
- **Combat**: someone was attacked near a planet in a nearby system and got away. Find the fleet that did
  it and destroy it. The job is done when it is destroyed or left with a quarter of its strength. The offer
  shows a glimpse of the fleet.
- **Marine operation**: a raid on a market hostile to the client to knock out a named industry (raiding
  costs standing with its owner), or garrison duty: bring your own marines to a market of the client's
  faction.
- **Expedition**: haul supplies to a camp at a planet in an uncolonised system. Fly close to the planet to
  deliver. Something may be waiting there; the client tells you how likely that is.
- **Defense**: a fleet is gathering outside a market of the client's faction. It attacks when you arrive in
  the system. Destroy it, or leave it with a quarter of its strength. The market's station fights with you.
  The offer shows a glimpse of the fleet.
- **Famous ship lead**: pay for the location of one named ship, sized by the tier. It is a pristine derelict
  you can recover (guarded from tier 3), or the flagship of a hostile fleet, always recoverable once beaten.
  S-mods from tier 3.
- **Ship deal**: a client wants one ship from your fleet (never the flagship) and pays well over its value on
  the spot, more for a ship Starship Legends rates as well known; or a client sells you a pristine ship
  (S-mods from tier 3) below its value. The offer shows the ship.
- **Conquest**: capture a market of a faction hostile to the client. Tier 5 and up, and only with
  Nexerelin, which adds invasions. Size 3 at tier 5, size 4 at tier 6, size 5 at tier 7. Story markets are
  never targets.
- **Salvage lead**: pay for the location of a debris field. Its loot grows with the tier, with colony items,
  AI cores and blueprints at high tiers. A few battered scavenger ships are already there.
- Clients you satisfy may offer to become contacts, more often at higher tiers.

## Tiers

| Tier | Pay | Hauling cargo | Enemy fleet | Fuel | Workers | Investment stake |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | 5k to 20k | 100 to 300 units | about 10 DP | 100 to 300 | 50 to 150 | 5k to 15k |
| 1 | 20k to 45k | 300 to 800 | about 25 DP | 300 to 600 | 150 to 300 | 15k to 35k |
| 2 | 45k to 80k | 800 to 1,500 | about 45 DP | 600 to 1,000 | 300 to 600 | 35k to 60k |
| 3 | 80k to 150k | 1,500 to 3,500 | about 90 DP | 1,000 to 1,800 | 600 to 1,000 | 60k to 120k |
| 4 | 150k to 300k | 3,500 to 7,000 | about 180 DP | 1,800 to 3,000 | 1,000 to 1,600 | 120k to 250k |
| 5 | 300k to 600k | 7,000 to 13,000 | about 300 DP | 3,000 to 4,500 | 1,600 to 2,500 | 250k to 500k |
| 6 | 700k to 1.5M | 13,000 to 24,000 | about 400 DP | 4,500 to 6,500 | 2,500 to 3,500 | 500k to 1.2M |
| 7 | 3M to 4M | 24,000 to 40,000 | about 500 DP | 6,500 to 10,000 | 3,500 to 5,000 | 2M to 3M |

Hauling, fuel runs and crew transport pay a third of the tier's pay: they rarely go wrong. LunaLib has a pay
multiplier for each job type, on top of the reward multiplier. It changes the money only, not the cargo or
marines a job asks for. Combat pays the
full amount up to tier 3, then 75 percent at tier 4, 60 at tier 5, 50 at tier 6 and 40 at tier 7 (1.2M to 1.6M).

Enemy fleets are measured in deployment points (DP), as in the refit screen. Raid targets are picked by ground defence: up to 150 at tier 0, 4,000 and more at tier 7.

Fuel and workers are capped so they are worth less than the pay, so low tiers often ask for less than
the table says.

## Settings (LunaLib)

- Chance of a mission board per bar.
- Lean toward your strength: 0 makes the tier roll fully random.
- Lowest and highest tier: bars only offer contracts in this range.
- Proving yourself (on by default): clients offer work one tier above what you have done. Finish 3
  contracts of a tier or higher (a setting) to open the next tier. Ship purchases and sales and investments
  do not count.
- Job types: turn each one on or off.
- Reward multiplier and enemy strength multiplier.
- Debug: test mode, off by default. Every bar gets a mission board with an option to spawn any job type at any tier (ignoring
  the job type and tier settings), and open contracts get buttons to complete or fail them at once. For
  testing, not for normal play.

## Removing the mod

Contracts and the bar offer are stored in your save. Removing the mod from a game in progress breaks
that save.
