import { createReducer, on } from '@ngrx/store';
import * as SprintActions from './sprint.actions';
import { BlockerRisk } from '../components/sprint-health/sprint-health.component';

export interface SprintState {
  confidenceScore: number;
  velocityTrend: number;
  blockerCount: number;
  activeRisks: BlockerRisk[];
  loading: boolean;
}

export const initialSprintState: SprintState = {
  confidenceScore: 92,
  velocityTrend: 44,
  blockerCount: 3,
  activeRisks: [
    {
      id: 'RISK-01',
      title: 'Stale work item ADO-1092 assigned to Rudra',
      severity: 'CRITICAL',
      assignedTo: 'Rudra',
      daysStale: 3
    },
    {
      id: 'RISK-02',
      title: 'QA testing bottle-neck on Epic feature deployment',
      severity: 'WARNING',
      assignedTo: 'QA Lead',
      daysStale: 2
    },
    {
      id: 'RISK-03',
      title: 'Build failure threshold exceeded in integration pipeline',
      severity: 'CRITICAL',
      assignedTo: 'DevOps Lead',
      daysStale: 1
    }
  ],
  loading: false
};

export const sprintReducer = createReducer(
  initialSprintState,
  on(SprintActions.loadSprintMetrics, state => ({ ...state, loading: true })),
  on(SprintActions.loadSprintMetricsSuccess, (state, action) => ({
    ...state,
    confidenceScore: action.confidenceScore,
    velocityTrend: action.velocityTrend,
    blockerCount: action.blockerCount,
    activeRisks: action.risks,
    loading: false
  })),
  on(SprintActions.executeRiskMitigationSuccess, (state, { riskId }) => ({
    ...state,
    activeRisks: state.activeRisks.filter(risk => risk.id !== riskId),
    blockerCount: Math.max(0, state.blockerCount - 1)
  }))
);
