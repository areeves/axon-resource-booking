export type QueryType = 'user' | 'resource' | 'id';

export type ResourceStatus = 'ACTIVE' | 'INACTIVE';

export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED';

export interface Resource {
  resourceId: string;
  name: string;
  description: string | null;
  location: string;
  capacity: number;
  status: ResourceStatus;
}

export interface Reservation {
  reservationId: string;
  resourceId: string;
  userId: string;
  start: string;
  end: string;
  status: ReservationStatus;
}

export interface AppState {
  credentials: { username: string; password: string } | null;
  actorId: string;
  resources: Resource[];
  selectedResourceId: string | null;
  queryType: QueryType;
  reservations: Reservation[];
}

export interface QueryOption {
  label: string;
  placeholder: string;
}

export interface DateTimeRange {
  start: string;
  end: string;
}

export interface AvailabilityTimeRange {
  start: string;
  end: string;
}

export interface AvailabilityWindow {
  windowId: string;
  start: string;
  end: string;
  type: 'BLACKOUT' | 'EXTRA';
  reason: string | null;
}

export interface ResourceAvailabilityRules {
  timezone: string | null;
  weeklyPattern: Record<string, AvailabilityTimeRange[]>;
  blackouts: AvailabilityWindow[];
  extras: AvailabilityWindow[];
}

export interface ConnectionForm {
  username: string;
  password: string;
  actorId: string;
}

export interface NewResourceDraft {
  name: string;
  description: string;
  capacity: number;
  location: string;
}

export interface ReservationQuery {
  user: string;
  resource: string;
  id: string;
}

export interface UtilizationForm {
  resourceId: string;
  from: string;
  to: string;
}

export interface UtilizationRow {
  date: string;
  reservationCount: number;
  occupiedHours: number;
  utilizationPercent: number;
}

export interface ToastState {
  message: string;
  error: boolean;
  visible: boolean;
}