import {
  Component,
  OnInit,
  OnDestroy,
  AfterViewInit,
  ViewChild,
  ElementRef,
  inject,
  effect,
  signal,
  computed
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Filler,
  Tooltip,
  ChartConfiguration
} from 'chart.js';
import { NavbarComponent } from '../../shared/components/navbar/navbar.component';
import { loadAccount, loadAccounts } from '../../store/accounts/accounts.actions';
import { loadTransactions, loadMoreTransactions, selectTransaction } from '../../store/transactions/transactions.actions';
import { selectSelectedAccount, selectAccountsLoading, selectAllAccounts } from '../../store/accounts/accounts.selectors';
import {
  selectAllTransactions,
  selectHasMore,
  selectTransactionsLoading,
  selectTransactionsLoadingMore,
  selectTransactionCursor
} from '../../store/transactions/transactions.selectors';
import { TransactionResponse, AccountResponse, ExchangeRateResponse } from '../../core/models/account.model';
import { AccountApiService } from '../../core/services/account-api.service';

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Filler, Tooltip);

type ModalType = 'deposit' | 'withdraw' | 'transfer' | null;

@Component({
  selector: 'app-account-overview',
  standalone: true,
  imports: [CommonModule, FormsModule, NavbarComponent],
  templateUrl: './account-overview.component.html',
  styleUrl: './account-overview.component.scss'
})
export class AccountOverviewComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly accountApi = inject(AccountApiService);

  @ViewChild('balanceChart') chartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('sentinel') sentinel!: ElementRef<HTMLDivElement>;

  private chart: Chart | null = null;
  private observer: IntersectionObserver | null = null;
  private accountId!: string;

  readonly account = toSignal(this.store.select(selectSelectedAccount), { initialValue: null });
  readonly accountLoading = toSignal(this.store.select(selectAccountsLoading), { initialValue: false });
  readonly allAccounts = toSignal(this.store.select(selectAllAccounts), { initialValue: [] });
  readonly transactions = toSignal(this.store.select(selectAllTransactions), { initialValue: [] });
  readonly hasMore = toSignal(this.store.select(selectHasMore), { initialValue: false });
  readonly loading = toSignal(this.store.select(selectTransactionsLoading), { initialValue: false });
  readonly loadingMore = toSignal(this.store.select(selectTransactionsLoadingMore), { initialValue: false });
  readonly cursor = toSignal(this.store.select(selectTransactionCursor), { initialValue: null });

  // Other accounts for transfer target dropdown (exclude current)
  readonly otherAccounts = computed<AccountResponse[]>(() =>
    this.allAccounts().filter((a) => a.id !== this.accountId)
  );

  // Modal state
  activeModal = signal<ModalType>(null);
  modalAmount = signal('');
  modalTargetAccountId = signal('');
  modalDescription = signal('');
  modalLoading = signal(false);
  modalError = signal<string | null>(null);
  modalSuccess = signal<string | null>(null);

  // Transfer — exchange rate preview & confirmation
  exchangeRate = signal<number | null>(null);
  exchangeRateLoading = signal(false);
  showConfirmation = signal(false);
  selectedTargetAccount = signal<AccountResponse | null>(null);

  readonly receivedAmount = computed<number | null>(() => {
    const amount = parseFloat(this.modalAmount());
    const rate = this.exchangeRate();
    if (!amount || isNaN(amount) || amount <= 0 || !rate) return null;
    return parseFloat((amount * rate).toFixed(4));
  });

  constructor() {
    effect(() => {
      const txs = this.transactions();
      if (txs.length > 0 && this.chart) {
        this.updateChart(txs);
      }
    });
  }

  ngOnInit(): void {
    this.accountId = this.route.snapshot.paramMap.get('id')!;
    this.store.dispatch(loadAccount({ id: this.accountId }));
    this.store.dispatch(loadAccounts());
    this.store.dispatch(loadTransactions({ accountId: this.accountId }));
  }

  ngAfterViewInit(): void {
    this.initChart();
    this.initIntersectionObserver();
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
    this.observer?.disconnect();
  }

  // ── Modal helpers ──────────────────────────────────────────

  openModal(type: ModalType): void {
    this.activeModal.set(type);
    this.modalAmount.set('');
    this.modalDescription.set('');
    this.modalError.set(null);
    this.modalSuccess.set(null);
    this.modalLoading.set(false);
    this.showConfirmation.set(false);
    this.exchangeRate.set(null);
    this.selectedTargetAccount.set(null);

    const firstOther = this.otherAccounts()[0];
    this.modalTargetAccountId.set(firstOther?.id ?? '');
    if (type === 'transfer' && firstOther) {
      this.selectedTargetAccount.set(firstOther);
      this.fetchExchangeRate();
    }
  }

  closeModal(): void {
    this.activeModal.set(null);
    this.showConfirmation.set(false);
  }

  onTargetAccountChange(accountId: string): void {
    this.modalTargetAccountId.set(accountId);
    this.exchangeRate.set(null);
    const acc = this.otherAccounts().find(a => a.id === accountId) ?? null;
    this.selectedTargetAccount.set(acc);
    this.fetchExchangeRate();
  }

  private fetchExchangeRate(): void {
    const fromCurrency = this.account()?.currency;
    const target = this.selectedTargetAccount();
    if (!fromCurrency || !target || fromCurrency === target.currency) return;

    this.exchangeRateLoading.set(true);
    this.accountApi.getExchangeRates(fromCurrency, target.currency).subscribe({
      next: (result) => {
        const rate = Array.isArray(result)
          ? (result[0] as ExchangeRateResponse)?.rate
          : (result as ExchangeRateResponse).rate;
        this.exchangeRate.set(rate ?? null);
        this.exchangeRateLoading.set(false);
      },
      error: () => {
        this.exchangeRate.set(null);
        this.exchangeRateLoading.set(false);
      }
    });
  }

  submitModal(): void {
    const amountStr = this.modalAmount().trim();
    const amount = parseFloat(amountStr);
    if (!amountStr || isNaN(amount) || amount <= 0) {
      this.modalError.set('Please enter a valid positive amount.');
      return;
    }

    const type = this.activeModal();
    if (type === 'transfer' && !this.modalTargetAccountId()) {
      this.modalError.set('Please select a target account.');
      return;
    }

    // Transfer requires confirmation step first
    if (type === 'transfer' && !this.showConfirmation()) {
      this.modalError.set(null);
      this.showConfirmation.set(true);
      return;
    }

    this.modalError.set(null);
    this.modalLoading.set(true);

    const idempotencyKey = crypto.randomUUID();
    const description = this.modalDescription().trim() || undefined;
    let call$;

    if (type === 'deposit') {
      call$ = this.accountApi.deposit(this.accountId, amount, idempotencyKey, description);
    } else if (type === 'withdraw') {
      call$ = this.accountApi.withdraw(this.accountId, amount, idempotencyKey, description);
    } else {
      call$ = this.accountApi.exchange(
        this.accountId,
        this.modalTargetAccountId(),
        amount,
        idempotencyKey,
        description
      );
    }

    call$.subscribe({
      next: () => {
        this.modalLoading.set(false);
        this.modalSuccess.set('Transaction completed successfully!');
        this.store.dispatch(loadAccount({ id: this.accountId }));
        this.store.dispatch(loadTransactions({ accountId: this.accountId }));
        setTimeout(() => this.closeModal(), 1200);
      },
      error: (err) => {
        this.modalLoading.set(false);
        this.showConfirmation.set(false);
        const msg =
          err?.error?.error?.message ||   // { error: { error: { message } } }
          err?.error?.message ||           // { error: { message } }
          err?.message ||
          'Operation failed. Please try again.';
        this.modalError.set(msg);
      }
    });
  }

  // ── Chart ──────────────────────────────────────────────────

  private initChart(): void {
    if (!this.chartCanvas) return;
    const ctx = this.chartCanvas.nativeElement.getContext('2d');
    if (!ctx) return;

    const gradient = ctx.createLinearGradient(0, 0, 0, 300);
    gradient.addColorStop(0, 'rgba(255, 107, 53, 0.25)');
    gradient.addColorStop(1, 'rgba(255, 107, 53, 0.0)');

    const config: ChartConfiguration<'line'> = {
      type: 'line',
      data: {
        labels: [],
        datasets: [
          {
            label: 'Balance',
            data: [],
            borderColor: '#FF6B35',
            backgroundColor: gradient,
            fill: true,
            tension: 0.4,
            pointBackgroundColor: '#FF6B35',
            pointBorderColor: '#fff',
            pointBorderWidth: 2,
            pointRadius: 4,
            pointHoverRadius: 6
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
          mode: 'index',
          intersect: false
        },
        plugins: {
          tooltip: {
            backgroundColor: '#FFFFFF',
            borderColor: 'rgba(255, 107, 53, 0.3)',
            borderWidth: 1,
            titleColor: '#1A1A2E',
            bodyColor: 'rgba(26, 26, 46, 0.7)',
            padding: 12,
            callbacks: {
              label: (context) => {
                const value = context.parsed.y ?? 0;
                return ` Balance: ${value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
              }
            }
          },
          legend: { display: false }
        },
        scales: {
          x: {
            ticks: {
              color: 'rgba(26, 26, 46, 0.5)',
              maxTicksLimit: 8,
              font: { size: 11 }
            },
            grid: { color: 'rgba(0, 0, 0, 0.06)' }
          },
          y: {
            min: 0,
            ticks: {
              color: 'rgba(26, 26, 46, 0.5)',
              font: { size: 11 },
              callback: (value) =>
                Number(value).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
            },
            grid: { color: 'rgba(0, 0, 0, 0.06)' }
          }
        }
      }
    };

    this.chart = new Chart(ctx, config);

    const txs = this.transactions();
    if (txs.length > 0) {
      this.updateChart(txs);
    }
  }

  private niceStep(maxVal: number): number {
    if (maxVal <= 0) return 1;
    const rawStep = maxVal / 5;
    const magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
    const norm = rawStep / magnitude;
    const nice = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10;
    return nice * magnitude;
  }

  private updateChart(transactions: TransactionResponse[]): void {
    if (!this.chart) return;
    const sorted = [...transactions].sort(
      (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
    );
    this.chart.data.labels = sorted.map((tx) => this.formatDateShort(tx.createdAt));
    this.chart.data.datasets[0].data = sorted.map((tx) => tx.balanceAfter);

    const maxBalance = Math.max(...sorted.map(tx => tx.balanceAfter), 0);
    const step = this.niceStep(maxBalance);
    const niceMax = Math.ceil(maxBalance / step) * step;

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const yScale = this.chart.options.scales?.['y'] as any;
    if (yScale) {
      yScale.min = 0;
      yScale.max = niceMax;
      yScale.ticks.stepSize = step;
    }

    this.chart.update();
  }

  private initIntersectionObserver(): void {
    if (!this.sentinel) return;
    this.observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (entry.isIntersecting && this.hasMore() && !this.loadingMore() && !this.loading()) {
          const currentCursor = this.cursor();
          if (currentCursor) {
            this.store.dispatch(
              loadMoreTransactions({ accountId: this.accountId, cursor: currentCursor })
            );
          }
        }
      },
      { threshold: 0 }
    );
    this.observer.observe(this.sentinel.nativeElement);
  }

  // ── UI helpers ──────────────────────────────────────────────

  onTransactionClick(tx: TransactionResponse): void {
    this.store.dispatch(selectTransaction({ transaction: tx }));
    this.router.navigate(['/accounts', this.accountId, 'transactions', tx.id]);
  }

  goBack(): void {
    this.router.navigate(['/']);
  }

  private readonly CURRENCY_SYMBOLS: Record<string, string> = {
    EUR: '€', USD: '$', GBP: '£', SEK: 'kr', VND: '₫'
  };

  currencySymbol(code: string): string {
    return this.CURRENCY_SYMBOLS[code] ?? code;
  }

  formatBalance(balance: number, currency: string): string {
    const symbol = this.currencySymbol(currency);
    return `${currency} ${symbol}${balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  formatDateShort(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric'
    });
  }

  formatAmount(tx: TransactionResponse): string {
    const sign = this.isCredit(tx) ? '+' : '-';
    return `${sign}${tx.currency} ${tx.amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  isCredit(tx: TransactionResponse): boolean {
    return tx.type === 'DEPOSIT' || tx.type === 'EXCHANGE_IN';
  }

  getTypeClass(type: string): string {
    switch (type) {
      case 'DEPOSIT': return 'badge-success';
      case 'WITHDRAWAL': return 'badge-danger';
      case 'EXCHANGE_IN': return 'badge-info';
      case 'EXCHANGE_OUT': return 'badge-warning';
      default: return 'badge-info';
    }
  }

  getStatusClass(status: string): string {
    return status === 'ACTIVE' ? 'badge-success' :
           status === 'CLOSED' ? 'badge-danger' :
           status === 'BLOCKED' ? 'badge-warning' : 'badge-info';
  }
}
