"""Desktop side of the M3 same-card offline conflict-merge demo.

Downloads the current server collection (the shared BASE, before the phone's
offline review is uploaded) into a fresh temp desktop profile, reviews the
SAME card the phone just reviewed offline (card id passed as argv[1]) with
answer *Easy* (ease 4) so the desktop leaves a clearly different, more-advanced
scheduling state, then uploads it back to the running local anki-sync-server.

Because the desktop review happens AFTER the phone's offline review in
wall-clock time, the documented conflict rule (more-recent review wins) makes
the desktop the winner. Both revlog rows still persist (distinct ms ids), so no
review is lost or double-counted.

Run with the ankiCFA desktop pyenv from the desktop repo root:
    out/pyenv/bin/python /tmp/cfa_desktop_conflict_review.py <card_id>
"""

from __future__ import annotations

import json
import sys
import tempfile
import time

sys.path.insert(0, "pylib")
sys.path.insert(0, "out/pylib")

from anki.collection import Collection
from anki import cfa_sync as cs

SERVER_INFO = json.load(open("/tmp/cfa-syncserver/server-info.json"))
ENDPOINT = SERVER_INFO["host_endpoint"]
USER = SERVER_INFO["user"]
PASSWORD = SERVER_INFO["password"]


def main() -> int:
    cid = int(sys.argv[1])
    tmp = tempfile.mkdtemp(prefix="cfa-desktop-m3-")
    col = Collection(f"{tmp}/collection.anki2")
    try:
        auth = col.sync_login(USER, PASSWORD, ENDPOINT)
        # Pull the authoritative BASE server state (phone's offline review is
        # NOT here yet — the phone is in airplane mode).
        cs.force_full_download(col, auth)
        col.close()
        col = Collection(f"{tmp}/collection.anki2")

        base_revlog = col.db.scalar("select count(*) from revlog")
        base_card_rows = col.db.all(
            "select id,ease from revlog where cid=? order by id", cid
        )
        card = col.get_card(cid)
        reps_before = card.reps
        # Force the exact same card due and answer it Easy through the real
        # scheduler (writes a genuine graded revlog row).
        col.sched.set_due_date([cid], "0")
        col.decks.set_current(card.did)
        target = col.get_card(cid)
        target.start_timer()
        col.sched.answerCard(target, 4)  # 4 = Easy in v3

        after_card = col.get_card(cid)
        mid_revlog = col.db.scalar("select count(*) from revlog")
        d_id = col.db.scalar(
            "select id from revlog where cid=? order by id desc limit 1", cid
        )

        col.save()
        auth2 = col.sync_login(USER, PASSWORD, ENDPOINT)
        out = cs.sync(col, auth2)
        after_revlog = col.db.scalar("select count(*) from revlog")

        marker = {
            "card_id": cid,
            "front": col.get_card(cid).note()["Front"][:60],
            "base_revlog_for_card": base_card_rows,
            "reps_before": reps_before,
            "reps_after": after_card.reps,
            "desktop_answer": "Easy(4)",
            "desktop_revlog_id": d_id,
            "desktop_card_queue": after_card.queue,
            "desktop_card_type": after_card.type,
            "desktop_card_ivl": after_card.ivl,
            "revlog_base": base_revlog,
            "revlog_after_review": mid_revlog,
            "revlog_after_sync": after_revlog,
            "sync_result": out,
            "reviewed_at_ms": int(time.time() * 1000),
        }
        print("DESKTOP_CONFLICT_MARKER " + json.dumps(marker))
        return 0
    finally:
        try:
            col.close()
        except Exception:
            pass


if __name__ == "__main__":
    raise SystemExit(main())
