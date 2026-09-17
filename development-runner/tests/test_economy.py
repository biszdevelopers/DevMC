from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from launcher.economy import (
    all_transactions,
    daily_flows,
    flow_metrics,
    load_economy,
    player_flows,
    reason_flows,
    wealth_metrics,
)


class EconomyTests(unittest.TestCase):
    def test_loads_sessions_accounts_and_skips_bad_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            transaction_dir = root / "transactions" / "currency"
            transaction_dir.mkdir(parents=True)
            (transaction_dir / "session.json").write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "session_id": "session-one",
                        "started_at": 1_700_000_000_000,
                        "ended_at": 1_700_000_100_000,
                        "transactions": [
                            transaction("one", "give", 100, 0, 100, "daily_reward"),
                            {"operation": "invalid"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            (transaction_dir / "broken.json").write_text("not json", encoding="utf-8")
            (root / "players.json").write_text(
                json.dumps(
                    {
                        "one": {"displayName": "Alice", "purse": {"amount": 100}},
                        "two": {"name": "Bob", "purse": {"amount": 0}},
                    }
                ),
                encoding="utf-8",
            )

            data = load_economy(root)

            self.assertEqual(1, len(data.sessions))
            self.assertEqual(1, len(data.sessions[0].transactions))
            self.assertEqual("Alice", data.accounts["one"].name)
            self.assertEqual(2, len(data.errors))

    def test_calculates_flow_daily_reason_player_and_wealth_metrics(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            transaction_dir = root / "transactions" / "currency"
            transaction_dir.mkdir(parents=True)
            document = {
                "schema": 1,
                "session_id": "metrics",
                "started_at": 1_700_000_000_000,
                "ended_at": 1_700_100_000_000,
                "transactions": [
                    transaction("one", "give", 100, 0, 100, "daily_reward", 1_700_000_000_000),
                    transaction("two", "give", 50, 0, 50, "quest", 1_700_000_100_000),
                    transaction("one", "remove", 40, 100, 60, "shop", 1_700_090_000_000),
                ],
            }
            (transaction_dir / "metrics.json").write_text(json.dumps(document), encoding="utf-8")
            (root / "players.json").write_text(
                json.dumps(
                    {
                        "one": {"name": "Alice", "purse": {"amount": 60}},
                        "two": {"name": "Bob", "purse": {"amount": 50}},
                        "zero": {"name": "Zero", "purse": {"amount": 0}},
                    }
                ),
                encoding="utf-8",
            )
            data = load_economy(root)
            values = all_transactions(data.sessions)

            flow = flow_metrics(values)
            self.assertEqual((150, 40, 110, 190), (flow.created, flow.destroyed, flow.net, flow.volume))
            self.assertEqual(3, flow.transaction_count)
            self.assertEqual(2, flow.active_players)
            self.assertEqual(50, flow.median_transaction)
            self.assertEqual(100, flow.largest_transaction)
            self.assertEqual(2, len(daily_flows(values)))
            self.assertEqual("daily_reward", reason_flows(values)[0][0])
            self.assertEqual("Alice", player_flows(values, data.accounts)[0][0])

            wealth = wealth_metrics(data.accounts.values())
            self.assertEqual(110, wealth.total_supply)
            self.assertEqual(2, wealth.holders)
            self.assertEqual(60, wealth.largest_balance)
            self.assertAlmostEqual(60 / 110, wealth.top_holder_share)
            self.assertGreater(wealth.gini, 0)


def transaction(
    player_id: str,
    operation: str,
    amount: int,
    before: int,
    after: int,
    reason: str,
    occurred_at: int = 1_700_000_000_000,
) -> dict[str, object]:
    change = amount if operation == "give" else -amount
    return {
        "occurred_at": occurred_at,
        "player_id": player_id,
        "operation": operation,
        "amount": amount,
        "change": change,
        "balance_before": before,
        "balance_after": after,
        "reason": reason,
    }


if __name__ == "__main__":
    unittest.main()
