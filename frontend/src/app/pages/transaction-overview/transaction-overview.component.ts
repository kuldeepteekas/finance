import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { Store } from '@ngrx/store';
import { toSignal } from '@angular/core/rxjs-interop';
import jsPDF from 'jspdf';
import { NavbarComponent } from '../../shared/components/navbar/navbar.component';
import { selectSelectedTransaction } from '../../store/transactions/transactions.selectors';
import { TransactionResponse } from '../../core/models/account.model';

@Component({
  selector: 'app-transaction-overview',
  standalone: true,
  imports: [CommonModule, NavbarComponent],
  templateUrl: './transaction-overview.component.html',
  styleUrl: './transaction-overview.component.scss'
})
export class TransactionOverviewComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly transaction = toSignal(this.store.select(selectSelectedTransaction), { initialValue: null });

  ngOnInit(): void {
    // If no transaction in store, the template will show error state
  }

  goBack(): void {
    const accountId = this.route.snapshot.paramMap.get('accountId');
    if (accountId) {
      this.router.navigate(['/accounts', accountId]);
    } else {
      this.router.navigate(['/']);
    }
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

  formatAmount(amount: number, currency: string): string {
    return `${currency} ${amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
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

  exportPDF(): void {
    const tx = this.transaction();
    if (!tx) return;

    const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
    const pageWidth = doc.internal.pageSize.getWidth();
    const margin = 20;
    let y = margin;

    // Header bar
    doc.setFillColor(255, 107, 53);
    doc.rect(0, 0, pageWidth, 18, 'F');

    // Title
    doc.setTextColor(255, 255, 255);
    doc.setFontSize(14);
    doc.setFont('helvetica', 'bold');
    doc.text('FinanceApp – Transaction Receipt', margin, 12);

    y = 30;
    doc.setTextColor(30, 30, 50);
    doc.setFontSize(18);
    doc.text('Transaction Details', margin, y);

    y += 4;
    doc.setDrawColor(255, 107, 53);
    doc.setLineWidth(0.8);
    doc.line(margin, y, pageWidth - margin, y);

    y += 10;

    const fields: { label: string; value: string }[] = [
      { label: 'Transaction ID', value: tx.id },
      { label: 'Account ID', value: tx.accountId },
      { label: 'Type', value: tx.type.replace(/_/g, ' ') },
      { label: 'Status', value: tx.status },
      { label: 'Amount', value: this.formatAmount(tx.amount, tx.currency) },
      { label: 'Currency', value: tx.currency },
      { label: 'Balance Before', value: this.formatAmount(tx.balanceBefore, tx.currency) },
      { label: 'Balance After', value: this.formatAmount(tx.balanceAfter, tx.currency) },
      { label: 'Description', value: tx.description || 'N/A' },
      { label: 'Failure Reason', value: tx.failureReason || 'N/A' },
      { label: 'Correlation ID', value: tx.correlationId },
      ...(tx.counterpartyAccountId
        ? [{ label: tx.type === 'EXCHANGE_IN' ? 'From Account' : 'To Account', value: tx.counterpartyAccountId }]
        : []),
      { label: 'Idempotency Key', value: tx.idempotencyKey },
      { label: 'External Call Status', value: tx.externalCallStatus },
      { label: 'Date', value: this.formatDate(tx.createdAt) }
    ];

    fields.forEach((field, index) => {
      const bgColor = index % 2 === 0 ? 250 : 244;
      doc.setFillColor(bgColor, bgColor, bgColor);
      doc.rect(margin, y - 5, pageWidth - margin * 2, 9, 'F');

      doc.setFontSize(10);
      doc.setFont('helvetica', 'bold');
      doc.setTextColor(80, 80, 80);
      doc.text(field.label, margin + 2, y);

      doc.setFont('helvetica', 'normal');
      doc.setTextColor(30, 30, 50);
      const maxWidth = pageWidth - margin * 2 - 60;
      const valueText = doc.splitTextToSize(field.value, maxWidth);
      doc.text(valueText, margin + 60, y);

      y += 10;
    });

    y += 8;
    doc.setDrawColor(220, 220, 220);
    doc.setLineWidth(0.3);
    doc.line(margin, y, pageWidth - margin, y);
    y += 6;
    doc.setFontSize(8);
    doc.setTextColor(150, 150, 150);
    doc.setFont('helvetica', 'italic');
    doc.text(`Generated on ${new Date().toLocaleString('en-US')} by FinanceApp`, margin, y);

    doc.save(`transaction-${tx.id}.pdf`);
  }
}
