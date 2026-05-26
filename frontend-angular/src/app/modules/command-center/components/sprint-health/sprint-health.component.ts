import { Component, OnInit, OnDestroy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { 
  selectSprintBurndown, 
  selectSprintConfidence, 
  selectActiveRisks, 
  selectVelocityTrend,
  selectBlockerCount
} from '../../state/sprint.selectors';

export interface BlockerRisk {
  id: string;
  title: string;
  severity: 'CRITICAL' | 'WARNING' | 'LOW';
  assignedTo: string;
  daysStale: number;
}

@Component({
  selector: 'app-sprint-health',
  templateUrl: './sprint-health.component.html',
  styleUrls: ['./sprint-health.component.css']
})
export class SprintHealthComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  // RxJS Observables from state management
  confidenceScore$: Observable<number>;
  burndownData$: Observable<any>;
  activeRisks$: Observable<BlockerRisk[]>;
  velocityTrend$: Observable<number>;
  blockersCount$: Observable<number>;

  constructor(private store: Store) {
    this.confidenceScore$ = this.store.select(selectSprintConfidence).pipe(takeUntil(this.destroy$));
    this.burndownData$ = this.store.select(selectSprintBurndown).pipe(takeUntil(this.destroy$));
    this.activeRisks$ = this.store.select(selectActiveRisks).pipe(takeUntil(this.destroy$));
    this.velocityTrend$ = this.store.select(selectVelocityTrend).pipe(takeUntil(this.destroy$));
    this.blockersCount$ = this.store.select(selectBlockerCount).pipe(takeUntil(this.destroy$));
  }

  ngOnInit(): void {
    // Dispatch action to pull current sprint metrics
    this.store.dispatch({ type: '[Sprint Health] Load Current Sprint Metrics' });
  }

  getSeverityBadgeClass(severity: 'CRITICAL' | 'WARNING' | 'LOW'): string {
    switch (severity) {
      case 'CRITICAL':
        return 'bg-red-100 text-red-800 border border-red-200';
      case 'WARNING':
        return 'bg-amber-100 text-amber-800 border border-amber-200';
      default:
        return 'bg-green-100 text-green-800 border border-green-200';
    }
  }

  executeRiskMitigation(riskId: string): void {
    // Autonomous system action approval (Human-In-The-Loop)
    this.store.dispatch({
      type: '[Sprint Health] Execute Risk Mitigation',
      payload: { riskId }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
