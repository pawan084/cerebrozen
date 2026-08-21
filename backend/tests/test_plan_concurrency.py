"""`GET /plans/active` is a read that writes — so it has to write only once.

The endpoint mints a plan when the account has none, "so the app always has a
plan". That convenience carried a race: concurrent callers all saw no active
plan, all called `generate_plan`, and since generating DEACTIVATES whatever it
finds, the last commit won and the rest were orphaned. Six parallel reads made
six plans.

The damage is not a few stray rows. A client that had already rendered one of
the losers went on ticking steps against a plan the server no longer considered
active: `PATCH /plans/steps/{id}` returned 200, the checkbox went green, and the
next load showed a different plan with nothing done. The person's progress
silently evaporated. That is how this was found — the browser write-path test
`app.spec.ts::ticking a plan step reaches the server` failed exactly that way.

Both halves are pinned here: that parallel readers agree on one plan, and that a
tick survives the reads that come after it.
"""

import asyncio

import pytest


@pytest.mark.asyncio
async def test_parallel_reads_agree_on_a_single_plan(auth_client):
    responses = await asyncio.gather(
        *(auth_client.get("/plans/active") for _ in range(6))
    )
    assert all(r.status_code == 200 for r in responses), [r.status_code for r in responses]

    ids = {r.json()["id"] for r in responses}
    assert len(ids) == 1, (
        f"six parallel reads minted {len(ids)} different plans — whoever loses this "
        "race keeps ticking steps on a plan the server has already deactivated"
    )


@pytest.mark.asyncio
async def test_a_ticked_step_survives_the_reads_that_follow(auth_client):
    """The symptom, from the person's side rather than the row count.

    Ordered the way a client actually hits it: the parallel reads come FIRST,
    while the account still has no plan, because that is the only moment the
    race is live. Tick a step on the plan a reader was handed — exactly what the
    screen does with the plan it rendered — and then ask what is active.

    Written the other way round (tick first, then read in parallel) this passed
    with the fix removed, which is worth stating: by then a plan exists, nothing
    generates, and there is nothing left to race.
    """
    served = await asyncio.gather(*(auth_client.get("/plans/active") for _ in range(6)))
    rendered = served[0].json()
    step = rendered["steps"][0]

    toggled = await auth_client.patch(f"/plans/steps/{step['id']}", json={"done": True})
    assert toggled.status_code == 200, toggled.text

    after = (await auth_client.get("/plans/active")).json()
    assert after["id"] == rendered["id"], (
        "the plan handed to the client is no longer the active one — its ticks land "
        "on a dead row and disappear on the next load"
    )
    assert any(s["done"] for s in after["steps"]), (
        "the step was ticked and accepted, and then no step was done — this is the "
        "vanishing-progress bug the browser test caught"
    )
