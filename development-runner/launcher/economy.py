from __future__ import annotations

import json
import statistics
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable


DEFAULT_SERVER_DATA_DIR = Path("D:/ServerData")


@dataclass(frozen=True)
class Transaction:
    occurred_at: int
    player_id: str
    operation: str
    amount: int
    change: int
    balance_before: int
    balance_after: int
    reason: str


@dataclass(frozen=True)
class TransactionSession:
    path: Path
    session_id: str
    started_at: int
    ended_at: int
    transactions: tuple[Transaction, ...]


@dataclass(frozen=True)
class PlayerAccount:
    player_id: str
    name: str
    balance: int


@dataclass(frozen=True)
class EconomyData:
    sessions: tuple[TransactionSession, ...]
    accounts: dict[str, PlayerAccount]
    errors: tuple[str, ...]


@dataclass(frozen=True)
class FlowMetrics:
    created: int
    destroyed: int
    net: int
    volume: int
    transaction_count: int
    active_players: int
    average_transaction: float
    median_transaction: float
    largest_transaction: int


@dataclass(frozen=True)
class WealthMetrics:
    total_supply: int
    holders: int
    average_balance: float
    median_balance: float
    largest_balance: int
    top_holder_share: float
    gini: float


def load_economy(data_dir: Path = DEFAULT_SERVER_DATA_DIR) -> EconomyData:
    errors: list[str] = []
    sessions: list[TransactionSession] = []
    transaction_dir = data_dir / "transactions" / "currency"
    if transaction_dir.exists():
        for path in sorted(transaction_dir.glob("*.json")):
            try:
                sessions.append(_load_session(path, errors))
            except (OSError, ValueError, TypeError, json.JSONDecodeError) as exception:
                errors.append(f"{path.name}: {exception}")

    accounts: dict[str, PlayerAccount] = {}
    player_path = data_dir / "players.json"
    if player_path.exists():
        try:
            raw_players = json.loads(player_path.read_text(encoding="utf-8"))
            if not isinstance(raw_players, dict):
                raise ValueError("players.json must contain an object")
            for player_id, value in raw_players.items():
                if not isinstance(value, dict):
                    continue
                purse = value.get("purse", {})
                balance = purse.get("amount", 0) if isinstance(purse, dict) else 0
                if not _is_integer(balance) or balance < 0:
                    continue
                name = value.get("displayName") or value.get("name") or player_id
                accounts[player_id] = PlayerAccount(player_id, str(name), balance)
        except (OSError, ValueError, TypeError, json.JSONDecodeError) as exception:
            errors.append(f"players.json: {exception}")

    sessions.sort(key=lambda session: (session.ended_at, session.started_at), reverse=True)
    return EconomyData(tuple(sessions), accounts, tuple(errors))


def _load_session(path: Path, errors: list[str]) -> TransactionSession:
    document = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(document, dict):
        raise ValueError("session root must be an object")
    started_at = _required_integer(document, "started_at")
    ended_at = _required_integer(document, "ended_at")
    session_id = str(document.get("session_id") or path.stem)
    raw_transactions = document.get("transactions", [])
    if not isinstance(raw_transactions, list):
        raise ValueError("transactions must be an array")

    transactions: list[Transaction] = []
    for index, raw in enumerate(raw_transactions):
        try:
            transactions.append(_parse_transaction(raw))
        except (ValueError, TypeError) as exception:
            errors.append(f"{path.name} transaction {index + 1}: {exception}")
    return TransactionSession(path, session_id, started_at, ended_at, tuple(transactions))


def _parse_transaction(raw: object) -> Transaction:
    if not isinstance(raw, dict):
        raise ValueError("entry must be an object")
    operation = str(raw.get("operation", "")).lower()
    if operation not in {"give", "remove"}:
        raise ValueError("operation must be give or remove")
    player_id = str(raw.get("player_id", "")).strip()
    reason = str(raw.get("reason", "")).strip()
    if not player_id or not reason:
        raise ValueError("player_id and reason are required")
    return Transaction(
        _required_integer(raw, "occurred_at"),
        player_id,
        operation,
        _required_integer(raw, "amount"),
        _required_integer(raw, "change"),
        _required_integer(raw, "balance_before"),
        _required_integer(raw, "balance_after"),
        reason,
    )


def _required_integer(value: dict[str, object], key: str) -> int:
    result = value.get(key)
    if not _is_integer(result):
        raise ValueError(f"{key} must be an integer")
    return result


def _is_integer(value: object) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def all_transactions(sessions: Iterable[TransactionSession]) -> tuple[Transaction, ...]:
    return tuple(
        sorted(
            (transaction for session in sessions for transaction in session.transactions),
            key=lambda transaction: transaction.occurred_at,
            reverse=True,
        )
    )


def flow_metrics(transactions: Iterable[Transaction]) -> FlowMetrics:
    values = tuple(transactions)
    created = sum(transaction.change for transaction in values if transaction.change > 0)
    destroyed = -sum(transaction.change for transaction in values if transaction.change < 0)
    amounts = [abs(transaction.change) for transaction in values]
    return FlowMetrics(
        created=created,
        destroyed=destroyed,
        net=created - destroyed,
        volume=created + destroyed,
        transaction_count=len(values),
        active_players=len({transaction.player_id for transaction in values}),
        average_transaction=statistics.fmean(amounts) if amounts else 0.0,
        median_transaction=statistics.median(amounts) if amounts else 0.0,
        largest_transaction=max(amounts, default=0),
    )


def wealth_metrics(accounts: Iterable[PlayerAccount]) -> WealthMetrics:
    balances = [account.balance for account in accounts]
    positive = [balance for balance in balances if balance > 0]
    total = sum(balances)
    return WealthMetrics(
        total_supply=total,
        holders=len(positive),
        average_balance=statistics.fmean(balances) if balances else 0.0,
        median_balance=statistics.median(balances) if balances else 0.0,
        largest_balance=max(balances, default=0),
        top_holder_share=max(balances, default=0) / total if total else 0.0,
        gini=_gini(balances),
    )


def _gini(balances: Iterable[int]) -> float:
    values = sorted(max(0, value) for value in balances)
    total = sum(values)
    if not values or total == 0:
        return 0.0
    count = len(values)
    weighted = sum((2 * index - count - 1) * value for index, value in enumerate(values, 1))
    return weighted / (count * total)


def daily_flows(transactions: Iterable[Transaction]) -> list[tuple[str, FlowMetrics]]:
    grouped: dict[str, list[Transaction]] = defaultdict(list)
    for transaction in transactions:
        day = datetime.fromtimestamp(transaction.occurred_at / 1000).astimezone().date().isoformat()
        grouped[day].append(transaction)
    return [(day, flow_metrics(grouped[day])) for day in sorted(grouped, reverse=True)]


def reason_flows(transactions: Iterable[Transaction]) -> list[tuple[str, FlowMetrics]]:
    grouped: dict[str, list[Transaction]] = defaultdict(list)
    for transaction in transactions:
        grouped[transaction.reason].append(transaction)
    values = [(reason, flow_metrics(group)) for reason, group in grouped.items()]
    return sorted(values, key=lambda value: value[1].volume, reverse=True)


def session_flows(
    sessions: Iterable[TransactionSession],
) -> list[tuple[TransactionSession, FlowMetrics]]:
    return [
        (session, flow_metrics(session.transactions))
        for session in sorted(sessions, key=lambda value: (value.ended_at, value.started_at))
    ]


def player_flows(
    transactions: Iterable[Transaction], accounts: dict[str, PlayerAccount]
) -> list[tuple[str, str, FlowMetrics, int]]:
    grouped: dict[str, list[Transaction]] = defaultdict(list)
    for transaction in transactions:
        grouped[transaction.player_id].append(transaction)
    values = []
    for player_id, group in grouped.items():
        account = accounts.get(player_id)
        name = account.name if account else player_id
        balance = account.balance if account else max(group, key=lambda value: value.occurred_at).balance_after
        values.append((name, player_id, flow_metrics(group), balance))
    return sorted(values, key=lambda value: value[2].volume, reverse=True)
