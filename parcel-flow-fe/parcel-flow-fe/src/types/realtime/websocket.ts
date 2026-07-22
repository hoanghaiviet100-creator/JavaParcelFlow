export interface WebSocketMessage<T = unknown> {
  topic: string;
  payload: T;
  timestamp: string;
}

export interface ParcelLocationUpdatePayload {
  parcelId: string;
  trackingCode: string;
  latitude: number;
  longitude: number;
  status: string;
}
