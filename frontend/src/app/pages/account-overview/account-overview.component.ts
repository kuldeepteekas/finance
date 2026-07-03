import {
  Component,
  OnInit,
  OnDestroy,
  AfterViewInit,
  ViewChild,
  ElementRef,
  inject,
  effect
} from '@angular/core';
import { CommonModule } from '@angular/common';
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
import { loadAccount } from '../../store/accounts/accounts.actions';
import { loadTransactions, loadMoreTransactions, selectTransaction } from '../../store/transactions/transactions.actions';
import { selectSelectedAccount, selectAccountsLoading } from '../../store/accounts/accounts.selectors';
import {
  selectAllTransactions,
  selectHasMore,
  selectTransactionsLoading,
  selectTransactionsLoadingMore,
  selectTransactionCursor
} from '../../store/transactions/transactions.selectors';
import { TransactionResponse } from '../../core/models/account.model';

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Filler, Tooltip);

@Component({
  selector: 'app-account-overview',
  standalone: true,
  imports: [CommonModule, NavbarComponent],
  templateUrl: './account-overview.component.html',
  styleUrl: './account-overview.component.scss'
})
export class AccountOverviewComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  @ViewChild('balanceChart') chartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('sentinel') sentinel!: ElementRef<HTMLDivElement>;

  private chart: Chart | null = null;
  private observer: IntersectionObserver | null = null;
  private accountId!: string;

  readonly account = toSignal(this.store.select(selectSelectedAccount), { initialValue: null });
  readonly accountLoading = toSignal(this.store.select(selectAccountsLoading), { initialValue: false });
  readonly transactions = toSignal(this.store.select(selectAllTransactions), { initialValue: [] });
  readonly hasMore = toSignal(this.store.select(selectHasMore), { initialValue: false });
  readonly loading = toSignal(this.store.select(selectTransactionsLoading), { initialValue: false });
  readonly loadingMore = toSignal(this.store.select(selectTransactionsLoadingMore), { initialValue: false });
  readonly cursor = toSignal(this.store.select(selectTransactionCursor), { initialValue: null });

  constructor() {
    // React to transaction changes to update chart
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

  private initChart(): void {
    if (!this.chartCanvas) return;
    const ctx = this.chartCanvas.nativeElement.getContext('2d');
    if (!ctx) return;

    const gradient = ctx.createLinearGradient(0, 0, 0, 300);
    gradient.addColorStop(0, 'rgba(255, 107, 53, 0.35)');
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
            backgroundColor: 'rgba(26, 26, 46, 0.95)',
            borderColor: 'rgba(255, 107, 53, 0.3)',
            borderWidth: 1,
            titleColor: '#ffffff',
            bodyColor: 'rgba(255, 255, 255, 0.7)',
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
              color: 'rgba(255, 255, 255, 0.5)',
              maxTicksLimit: 8,
              font: { size: 11 }
            },
            grid: { color: 'rgba(255, 255, 255, 0.05)' }
          },
          y: {
            ticks: {
              color: 'rgba(255, 255, 255, 0.5)',
              font: { size: 11 },
              callback: (value) =>
                Number(value).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
            },
            grid: { color: 'rgba(255, 255, 255, 0.05)' }
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

  private updateChart(transactions: TransactionResponse[]): void {
    if (!this.chart) return;
    const sorted = [...transactions].sort(
      (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
    );
    this.chart.data.labels = sorted.map((tx) => this.formatDateShort(tx.createdAt));
    this.chart.data.datasets[0].data = sorted.map((tx) => tx.balanceAfter);
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
      { threshold: 0.1 }
    );
    this.observer.observe(this.sentinel.nativeElement);
  }

  onTransactionClick(tx: TransactionResponse): void {
    this.store.dispatch(selectTransaction({ transaction: tx }));
    this.router.navigate(['/accounts', this.accountId, 'transactions', tx.id]);
  }

  goBack(): void {
    this.router.navigate(['/']);
  }

  formatBalance(balance: number, currency: string): string {
    return `${currency} ${balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
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
