# VoxGest Avatar — User-Facing Vocabulary Priority

Date: 2026-09-14
Owner: Astra Avatar lane

## Product rule

The Avatar exists primarily for the reverse communication path:

`HEARING USER SPEAKS -> STT -> SUPPORTED CONCEPT -> VERIFIED FSL AVATAR ACTION -> FSL USER`

Time is limited. Astra must prioritize **everyday communication value**, not impressive raw vocabulary count.

Use only canonical FSL-105 labels that actually exist in the authoritative vocabulary. The English/Filipino strings below are presentation text; they must **not silently rename model/action IDs**.

If a target concept is not an exact canonical FSL-105 class, mark it `NOT_AVAILABLE_AS_CANONICAL_CLASS` and continue. Do not invent a new sign from text alone.

## Tier 0 — revalidate before expansion

| Canonical action | English presentation | Filipino presentation | Status goal |
|---|---|---|---|
| `HELLO` | Hello | Kumusta | CORE3 regression |
| `MILK` | Milk | Gatas | CORE3 regression |
| `RICE` | Rice | Kanin / Bigas | CORE3 regression |

## Tier 1 — highest everyday value

Process source-clean candidates from this group first.

| Target canonical concept | English | Filipino |
|---|---|---|
| `THANK YOU` | Thank you | Salamat |
| `YES` | Yes | Oo |
| `NO` | No | Hindi |
| `PLEASE` | Please | Pakiusap / Paki- |
| `HELP` | Help | Tulong / Tulungan mo ako |
| `SORRY` | Sorry | Paumanhin / Pasensya na |
| `HOW ARE YOU` | How are you? | Kumusta ka? |
| `IM FINE` | I'm fine | Mabuti ako |
| `UNDERSTAND` | Understand | Naiintindihan |
| `DON'T UNDERSTAND` | Don't understand | Hindi ko naiintindihan |
| `KNOW` | Know | Alam |
| `DON'T KNOW` | Don't know | Hindi ko alam |
| `NAME` | Name | Pangalan |
| `MY` | My | Akin / Ko |
| `YOUR` | Your | Iyo / Mo |
| `STOP` | Stop | Hinto / Tumigil |
| `DOCTOR` | Doctor | Doktor |
| `WATER` | Water | Tubig |

## Tier 2 — conversational courtesy and time-of-day

| Target canonical concept | English | Filipino |
|---|---|---|
| `YOURE WELCOME` | You're welcome | Walang anuman |
| `GOOD MORNING` | Good morning | Magandang umaga |
| `GOOD AFTERNOON` | Good afternoon | Magandang hapon |
| `GOOD EVENING` | Good evening | Magandang gabi |

## Tier 3 — practical daily nouns / numbers

After Tier 1 and Tier 2, prefer clean source classes useful for food, healthcare, transactions, directions, family, common needs, and basic numbers. Rank them by:

- 35% communication value
- 30% source quality
- 20% retargeting simplicity
- 10% handshape stability
- 5% low occlusion/collision risk

## Fast-fail policy

For each candidate:

`SOURCE CLEAN? -> frozen solver -> mechanical QA -> visual QA -> runtime export -> Android proof`

If the source is poor, occluded, severely incomplete, or requires extensive one-off manual surgery, mark the candidate `SOURCE_REVIEW_REQUIRED` or `RETARGET_REVIEW_REQUIRED` and move to the next high-value sign.

Do not lower quality to hit an arbitrary count.

## Bilingual speech resolver rule

The spoken input may arrive as English or Filipino. Both should resolve to the same supported canonical concept where the mapping is deterministic.

Examples:

- `hello`, `hi`, `kumusta` -> canonical `HELLO`
- `thank you`, `thanks`, `salamat` -> canonical `THANK YOU`
- `yes`, `oo` -> canonical `YES`
- `no`, `hindi` -> canonical `NO`
- `help`, `tulong` -> canonical `HELP`
- `water`, `tubig` -> canonical `WATER`
- `doctor`, `doktor` -> canonical `DOCTOR`
- `milk`, `gatas` -> canonical `MILK`
- `rice`, `kanin`, `bigas` -> canonical `RICE`

Resolver behavior must remain conservative:

- exact/safe synonym mapping only
- unsupported text -> no fabricated Avatar action
- ambiguous phrase -> ask/reject rather than guess
- display the recognized transcript and resolved canonical concept separately in debug evidence

## Linguistic limitation

Engineering calibration is not equivalent to qualified FSL linguistic certification. Do not claim complete FSL grammar, unrestricted text-to-FSL translation, or validated facial/non-manual grammar.