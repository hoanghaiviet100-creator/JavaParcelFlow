export interface ParcelSummary {
  parcelCode: string;
  weight: number; // in kg
  description: string;
}

export interface TrackingEvent {
  id: string;
  status: string;
  description: string;
  locationName: string;
  timestamp: string;
}

export interface OrderTrackingInfo {
  orderCode: string;
  status: string;
  senderName: string;
  senderAddress: string;
  receiverName: string;
  receiverAddress: string;
  phoneNumber: string;
  parcels: ParcelSummary[];
  events: TrackingEvent[];
}

export interface GetTrackingResponse {
  success: boolean;
  message?: string;
  data: {
    tracking: OrderTrackingInfo;
  };
  timestamp?: string;
}
