import { createAction, props } from '@ngrx/store';
import { BlockerRisk } from '../components/sprint-health/sprint-health.component';

export const loadSprintMetrics = createAction(
  '[Sprint Health] Load Current Sprint Metrics'
);

export const loadSprintMetricsSuccess = createAction(
  '[Sprint Health] Load Current Sprint Metrics Success',
  props<{ 
    confidenceScore: number; 
    velocityTrend: number;
    blockerCount: number;
    risks: BlockerRisk[];
  }>()
);

export const executeRiskMitigation = createAction(
  '[Sprint Health] Execute Risk Mitigation',
  props<{ riskId: string }>()
);

export const executeRiskMitigationSuccess = createAction(
  '[Sprint Health] Execute Risk Mitigation Success',
  props<{ riskId: string }>()
);
