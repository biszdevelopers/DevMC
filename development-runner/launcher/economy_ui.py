from __future__ import annotations

import os
import tkinter as tk
from datetime import datetime
from pathlib import Path
from tkinter import messagebox, ttk

from .economy import (
    DEFAULT_SERVER_DATA_DIR,
    EconomyData,
    Transaction,
    TransactionSession,
    all_transactions,
    daily_flows,
    flow_metrics,
    load_economy,
    player_flows,
    reason_flows,
    session_flows,
    wealth_metrics,
)


class EconomyPane(ttk.Frame):
    """Read-only transaction explorer and economy-health dashboard."""

    def __init__(
        self,
        parent: tk.Misc,
        status_var: tk.StringVar,
        data_dir: Path = DEFAULT_SERVER_DATA_DIR,
    ) -> None:
        super().__init__(parent, padding=10)
        self.status_var = status_var
        self.data_dir = data_dir
        self.transaction_dir = data_dir / "transactions" / "currency"
        self.data = EconomyData((), {}, ())
        self.session_rows: dict[str, TransactionSession] = {}
        self.summary_vars: dict[str, tk.StringVar] = {}
        self.overall_vars: dict[str, tk.StringVar] = {}
        self.scope_var = tk.StringVar(value="Selected session")
        self.detail_var = tk.StringVar(value="No transaction sessions found.")
        self._build()
        self.refresh()

    def _build(self) -> None:
        toolbar = ttk.Frame(self)
        toolbar.pack(fill="x", pady=(0, 8))
        ttk.Label(toolbar, text="Currency transactions", style="Heading.TLabel").pack(side="left")
        ttk.Label(toolbar, text=str(self.transaction_dir)).pack(side="left", padx=(12, 0))
        ttk.Button(toolbar, text="Open Folder", command=self._open_folder).pack(side="right")
        ttk.Button(toolbar, text="Refresh", command=self.refresh).pack(side="right", padx=(0, 6))

        ttk.Label(self, textvariable=self.scope_var, style="Heading.TLabel").pack(anchor="w")
        cards = ttk.Frame(self)
        cards.pack(fill="x", pady=(4, 8))
        card_specs = (
            ("created", "Created"),
            ("destroyed", "Destroyed"),
            ("net", "Net change"),
            ("volume", "Gross volume"),
            ("transactions", "Transactions"),
            ("players", "Active players"),
            ("average", "Mean transfer"),
            ("median", "Median transfer"),
            ("largest", "Largest transfer"),
            ("ratio", "Mint / burn"),
        )
        for column, (key, label) in enumerate(card_specs):
            card = ttk.LabelFrame(cards, text=label, padding=(8, 4))
            card.grid(row=column // 5, column=column % 5, sticky="ew", padx=3, pady=2)
            value = tk.StringVar(value="—")
            self.summary_vars[key] = value
            ttk.Label(card, textvariable=value, font=("Segoe UI", 11, "bold")).pack()
        for column in range(5):
            cards.columnconfigure(column, weight=1)

        panes = ttk.Panedwindow(self, orient="horizontal")
        panes.pack(fill="both", expand=True)
        sessions_frame = ttk.LabelFrame(panes, text="Sessions", padding=6)
        detail_frame = ttk.Frame(panes)
        panes.add(sessions_frame, weight=2)
        panes.add(detail_frame, weight=5)

        self.sessions_tree = self._tree(
            sessions_frame,
            ("ended", "transactions", "created", "destroyed", "net"),
            ("Ended", "Tx", "Created", "Destroyed", "Net"),
            (145, 55, 90, 90, 90),
        )
        self.sessions_tree.bind("<<TreeviewSelect>>", self._session_selected)

        detail_notebook = ttk.Notebook(detail_frame)
        detail_notebook.pack(fill="both", expand=True)
        overall_tab = ttk.Frame(detail_notebook, padding=6)
        transaction_tab = ttk.Frame(detail_notebook, padding=6)
        daily_tab = ttk.Frame(detail_notebook, padding=6)
        reason_tab = ttk.Frame(detail_notebook, padding=6)
        player_tab = ttk.Frame(detail_notebook, padding=6)
        detail_notebook.add(overall_tab, text="Overall & Trends")
        detail_notebook.add(transaction_tab, text="Transactions")
        detail_notebook.add(daily_tab, text="Daily Flow")
        detail_notebook.add(reason_tab, text="Reasons")
        detail_notebook.add(player_tab, text="Players")

        overall_cards = ttk.Frame(overall_tab)
        overall_cards.pack(fill="x", pady=(0, 7))
        overall_specs = (
            ("created", "Total created"),
            ("destroyed", "Total destroyed"),
            ("net", "Total net"),
            ("volume", "Total volume"),
            ("transactions", "Transactions"),
            ("sessions", "Sessions"),
            ("supply", "Current supply"),
            ("holders", "Holders"),
            ("velocity", "Flow / supply"),
            ("gini", "Balance Gini"),
            ("top_share", "Top-holder share"),
            ("median", "Median transfer"),
        )
        for column, (key, label) in enumerate(overall_specs):
            card = ttk.LabelFrame(overall_cards, text=label, padding=(7, 3))
            card.grid(row=column // 6, column=column % 6, sticky="ew", padx=2, pady=2)
            value = tk.StringVar(value="—")
            self.overall_vars[key] = value
            ttk.Label(card, textvariable=value, font=("Segoe UI", 10, "bold")).pack()
        for column in range(6):
            overall_cards.columnconfigure(column, weight=1)
        self.trends = SessionTrendCharts(overall_tab)
        self.trends.pack(fill="both", expand=True)

        self.transactions_tree = self._tree(
            transaction_tab,
            ("time", "player", "reason", "operation", "amount", "before", "after"),
            ("Time", "Player", "Reason", "Operation", "Amount", "Before", "After"),
            (145, 115, 130, 72, 85, 90, 90),
        )
        self.daily_tree = self._tree(
            daily_tab,
            ("day", "transactions", "players", "created", "destroyed", "net", "volume"),
            ("Day", "Tx", "Players", "Created", "Destroyed", "Net", "Volume"),
            (100, 55, 60, 100, 100, 100, 100),
        )
        self.reasons_tree = self._tree(
            reason_tab,
            ("reason", "transactions", "players", "created", "destroyed", "net", "average"),
            ("Reason", "Tx", "Players", "Created", "Destroyed", "Net", "Average"),
            (180, 55, 60, 100, 100, 100, 90),
        )
        self.players_tree = self._tree(
            player_tab,
            ("player", "uuid", "transactions", "created", "destroyed", "net", "balance"),
            ("Player", "UUID", "Tx", "Created", "Destroyed", "Net", "Current balance"),
            (110, 230, 55, 95, 95, 95, 110),
        )
        ttk.Label(self, textvariable=self.detail_var).pack(anchor="w", pady=(6, 0))

    def _tree(
        self,
        parent: tk.Misc,
        columns: tuple[str, ...],
        headings: tuple[str, ...],
        widths: tuple[int, ...],
    ) -> ttk.Treeview:
        frame = ttk.Frame(parent)
        frame.pack(fill="both", expand=True)
        tree = ttk.Treeview(frame, columns=columns, show="headings", selectmode="browse")
        vertical = ttk.Scrollbar(frame, orient="vertical", command=tree.yview)
        horizontal = ttk.Scrollbar(frame, orient="horizontal", command=tree.xview)
        tree.configure(yscrollcommand=vertical.set, xscrollcommand=horizontal.set)
        tree.grid(row=0, column=0, sticky="nsew")
        vertical.grid(row=0, column=1, sticky="ns")
        horizontal.grid(row=1, column=0, sticky="ew")
        frame.rowconfigure(0, weight=1)
        frame.columnconfigure(0, weight=1)
        for column, heading, width in zip(columns, headings, widths):
            tree.heading(column, text=heading)
            tree.column(column, width=width, minwidth=45, anchor="e" if column not in {"time", "day", "player", "uuid", "reason"} else "w")
        tree.tag_configure("positive", foreground="#17833d")
        tree.tag_configure("negative", foreground="#b3261e")
        return tree

    def refresh(self) -> None:
        try:
            self.data = load_economy(self.data_dir)
        except Exception as exception:
            messagebox.showerror("Economy", f"Could not load transaction data:\n{exception}", parent=self)
            return
        self._populate_overall()
        self._populate_sessions()
        error_note = f" {len(self.data.errors)} malformed entries were skipped." if self.data.errors else ""
        self.status_var.set(f"Loaded {len(self.data.sessions)} currency transaction sessions.{error_note}")

    def _populate_sessions(self) -> None:
        self.sessions_tree.delete(*self.sessions_tree.get_children())
        self.session_rows.clear()
        for index, session in enumerate(self.data.sessions):
            metrics = flow_metrics(session.transactions)
            iid = f"session-{index}"
            self.session_rows[iid] = session
            self.sessions_tree.insert(
                "",
                "end",
                iid=iid,
                values=(
                    _time(session.ended_at),
                    metrics.transaction_count,
                    _nits(metrics.created),
                    _nits(metrics.destroyed),
                    _signed(metrics.net),
                ),
                tags=(_flow_tag(metrics.net),),
            )
        if self.data.sessions:
            self.sessions_tree.selection_set("session-0")
            self.sessions_tree.focus("session-0")
            self._show_session(self.data.sessions[0])
        else:
            self._clear_session()

    def _session_selected(self, _event: tk.Event) -> None:
        selection = self.sessions_tree.selection()
        if not selection:
            return
        session = self.session_rows.get(selection[0])
        if session is not None:
            self._show_session(session)

    def _show_session(self, session: TransactionSession) -> None:
        transactions = tuple(sorted(session.transactions, key=lambda value: value.occurred_at, reverse=True))
        self.scope_var.set(f"Session {session.session_id}")
        duration = max(0, session.ended_at - session.started_at)
        span = f"{_time(session.started_at)} → {_time(session.ended_at)} · {_duration(duration)}"

        flow = flow_metrics(transactions)
        self.summary_vars["created"].set(_nits(flow.created))
        self.summary_vars["destroyed"].set(_nits(flow.destroyed))
        self.summary_vars["net"].set(_signed(flow.net))
        self.summary_vars["volume"].set(_nits(flow.volume))
        self.summary_vars["transactions"].set(f"{flow.transaction_count:,}")
        self.summary_vars["players"].set(f"{flow.active_players:,}")
        self.summary_vars["average"].set(_nits(round(flow.average_transaction)))
        self.summary_vars["median"].set(_nits(round(flow.median_transaction)))
        self.summary_vars["largest"].set(_nits(flow.largest_transaction))
        ratio = "∞" if flow.destroyed == 0 and flow.created else f"{flow.created / flow.destroyed:.2f}" if flow.destroyed else "—"
        self.summary_vars["ratio"].set(ratio)
        self.detail_var.set(
            f"{span} · Session file: {session.path.name}"
        )
        self._populate_transactions(transactions)
        self._populate_daily(transactions)
        self._populate_reasons(transactions)
        self._populate_players(transactions)

    def _clear_session(self) -> None:
        self.scope_var.set("No transaction sessions")
        for value in self.summary_vars.values():
            value.set("—")
        self.detail_var.set("No transaction sessions found. Start and stop Currency once to create a journal.")
        for tree in (self.transactions_tree, self.daily_tree, self.reasons_tree, self.players_tree):
            tree.delete(*tree.get_children())

    def _populate_overall(self) -> None:
        transactions = all_transactions(self.data.sessions)
        flow = flow_metrics(transactions)
        wealth = wealth_metrics(self.data.accounts.values())
        velocity = flow.volume / wealth.total_supply if wealth.total_supply else 0.0
        self.overall_vars["created"].set(_nits(flow.created))
        self.overall_vars["destroyed"].set(_nits(flow.destroyed))
        self.overall_vars["net"].set(_signed(flow.net))
        self.overall_vars["volume"].set(_nits(flow.volume))
        self.overall_vars["transactions"].set(f"{flow.transaction_count:,}")
        self.overall_vars["sessions"].set(f"{len(self.data.sessions):,}")
        self.overall_vars["supply"].set(_nits(wealth.total_supply))
        self.overall_vars["holders"].set(f"{wealth.holders:,}")
        self.overall_vars["velocity"].set(f"{velocity:.2%}")
        self.overall_vars["gini"].set(f"{wealth.gini:.3f}")
        self.overall_vars["top_share"].set(f"{wealth.top_holder_share:.1%}")
        self.overall_vars["median"].set(_nits(round(flow.median_transaction)))
        self.trends.set_sessions(self.data.sessions)

    def _populate_transactions(self, transactions: tuple[Transaction, ...]) -> None:
        self.transactions_tree.delete(*self.transactions_tree.get_children())
        for index, transaction in enumerate(transactions):
            account = self.data.accounts.get(transaction.player_id)
            player = account.name if account else transaction.player_id
            self.transactions_tree.insert(
                "",
                "end",
                iid=f"transaction-{index}",
                values=(
                    _time(transaction.occurred_at),
                    player,
                    transaction.reason,
                    transaction.operation,
                    _signed(transaction.change),
                    _nits(transaction.balance_before),
                    _nits(transaction.balance_after),
                ),
                tags=(_flow_tag(transaction.change),),
            )

    def _populate_daily(self, transactions: tuple[Transaction, ...]) -> None:
        self.daily_tree.delete(*self.daily_tree.get_children())
        for index, (day, metrics) in enumerate(daily_flows(transactions)):
            self.daily_tree.insert(
                "",
                "end",
                iid=f"day-{index}",
                values=(day, metrics.transaction_count, metrics.active_players, _nits(metrics.created), _nits(metrics.destroyed), _signed(metrics.net), _nits(metrics.volume)),
                tags=(_flow_tag(metrics.net),),
            )

    def _populate_reasons(self, transactions: tuple[Transaction, ...]) -> None:
        self.reasons_tree.delete(*self.reasons_tree.get_children())
        for index, (reason, metrics) in enumerate(reason_flows(transactions)):
            self.reasons_tree.insert(
                "",
                "end",
                iid=f"reason-{index}",
                values=(reason, metrics.transaction_count, metrics.active_players, _nits(metrics.created), _nits(metrics.destroyed), _signed(metrics.net), _nits(round(metrics.average_transaction))),
                tags=(_flow_tag(metrics.net),),
            )

    def _populate_players(self, transactions: tuple[Transaction, ...]) -> None:
        self.players_tree.delete(*self.players_tree.get_children())
        for index, (name, player_id, metrics, balance) in enumerate(player_flows(transactions, self.data.accounts)):
            self.players_tree.insert(
                "",
                "end",
                iid=f"player-{index}",
                values=(name, player_id, metrics.transaction_count, _nits(metrics.created), _nits(metrics.destroyed), _signed(metrics.net), _nits(balance)),
                tags=(_flow_tag(metrics.net),),
            )

    def _open_folder(self) -> None:
        try:
            self.transaction_dir.mkdir(parents=True, exist_ok=True)
            os.startfile(self.transaction_dir)  # type: ignore[attr-defined]
        except OSError as exception:
            messagebox.showerror("Economy", f"Could not open transaction folder:\n{exception}", parent=self)


class SessionTrendCharts(ttk.Frame):
    def __init__(self, parent: tk.Misc) -> None:
        super().__init__(parent)
        notebook = ttk.Notebook(self)
        notebook.pack(fill="both", expand=True)
        self.charts: dict[str, TrendCanvas] = {}
        for key, title in (
            ("net", "Net Change"),
            ("mint_burn", "Created vs Destroyed"),
            ("volume", "Gross Volume"),
            ("transactions", "Transaction Count"),
        ):
            chart = TrendCanvas(notebook)
            notebook.add(chart, text=title)
            self.charts[key] = chart

    def set_sessions(self, sessions: tuple[TransactionSession, ...]) -> None:
        rows = session_flows(sessions)
        labels = tuple(datetime.fromtimestamp(session.ended_at / 1000).astimezone().strftime("%m-%d\n%H:%M") for session, _metrics in rows)
        self.charts["net"].set_data(labels, (("Net", tuple(metrics.net for _session, metrics in rows), "#1769aa"),))
        self.charts["mint_burn"].set_data(
            labels,
            (
                ("Created", tuple(metrics.created for _session, metrics in rows), "#17833d"),
                ("Destroyed", tuple(metrics.destroyed for _session, metrics in rows), "#b3261e"),
            ),
        )
        self.charts["volume"].set_data(labels, (("Volume", tuple(metrics.volume for _session, metrics in rows), "#7b4ab5"),))
        self.charts["transactions"].set_data(
            labels,
            (("Transactions", tuple(metrics.transaction_count for _session, metrics in rows), "#b56b00"),),
        )


class TrendCanvas(tk.Canvas):
    def __init__(self, parent: tk.Misc) -> None:
        super().__init__(parent, background="#ffffff", highlightthickness=0, height=280)
        self.labels: tuple[str, ...] = ()
        self.series: tuple[tuple[str, tuple[int, ...], str], ...] = ()
        self.bind("<Configure>", lambda _event: self._draw())

    def set_data(
        self,
        labels: tuple[str, ...],
        series: tuple[tuple[str, tuple[int, ...], str], ...],
    ) -> None:
        self.labels = labels
        self.series = series
        self._draw()

    def _draw(self) -> None:
        self.delete("all")
        width = max(self.winfo_width(), 520)
        height = max(self.winfo_height(), 260)
        left, top, right, bottom = 72, 30, width - 24, height - 48
        if not self.labels:
            self.create_text(width / 2, height / 2, text="No session data yet", fill="#666666")
            return

        values = [value for _name, points, _color in self.series for value in points]
        low = min(0, min(values, default=0))
        high = max(0, max(values, default=0))
        if low == high:
            high = low + 1

        def x_at(index: int) -> float:
            return (left + right) / 2 if len(self.labels) == 1 else left + (right - left) * index / (len(self.labels) - 1)

        def y_at(value: float) -> float:
            return bottom - (value - low) * (bottom - top) / (high - low)

        for step in range(6):
            value = low + (high - low) * step / 5
            y = y_at(value)
            self.create_line(left, y, right, y, fill="#e5e7eb")
            self.create_text(left - 8, y, text=_compact(value), anchor="e", fill="#555555")
        zero_y = y_at(0)
        self.create_line(left, zero_y, right, zero_y, fill="#8a8a8a", width=2)

        label_step = max(1, (len(self.labels) + 7) // 8)
        for index, label in enumerate(self.labels):
            if index % label_step == 0 or index == len(self.labels) - 1:
                self.create_text(x_at(index), bottom + 20, text=label, anchor="n", fill="#555555", justify="center")

        legend_x = left
        for name, points, color in self.series:
            coordinates: list[float] = []
            for index, value in enumerate(points):
                coordinates.extend((x_at(index), y_at(value)))
            if len(coordinates) >= 4:
                self.create_line(*coordinates, fill=color, width=2, smooth=False)
            for index, value in enumerate(points):
                x, y = x_at(index), y_at(value)
                self.create_oval(x - 3, y - 3, x + 3, y + 3, fill=color, outline=color)
            self.create_line(legend_x, 13, legend_x + 20, 13, fill=color, width=3)
            self.create_text(legend_x + 25, 13, text=name, anchor="w", fill="#333333")
            legend_x += 25 + max(70, len(name) * 8)


def _nits(value: int) -> str:
    return f"{value:,} N"


def _signed(value: int) -> str:
    return f"{value:+,} N"


def _flow_tag(value: int) -> str:
    return "positive" if value > 0 else "negative" if value < 0 else ""


def _time(milliseconds: int) -> str:
    return datetime.fromtimestamp(milliseconds / 1000).astimezone().strftime("%Y-%m-%d %H:%M:%S")


def _duration(milliseconds: int) -> str:
    seconds = milliseconds // 1000
    hours, remainder = divmod(seconds, 3600)
    minutes, seconds = divmod(remainder, 60)
    return f"{hours}h {minutes}m {seconds}s"


def _compact(value: float) -> str:
    magnitude = abs(value)
    if magnitude >= 1_000_000_000:
        return f"{value / 1_000_000_000:.1f}B"
    if magnitude >= 1_000_000:
        return f"{value / 1_000_000:.1f}M"
    if magnitude >= 1_000:
        return f"{value / 1_000:.1f}K"
    return f"{value:.0f}"
