import { http, HttpResponse } from "msw";
import { env } from "@/config/env";
import { OrderTrackingInfo } from "@/features/tracking/types/tracking.types";

const baseUrl = env.apiBaseUrl.replace(/\/$/, "");
const prefix = env.apiPrefix.startsWith("/") ? env.apiPrefix : `/${env.apiPrefix}`;
const apiPath = (path: string) => `${baseUrl}${prefix}${path}`;

// Simple in-memory session backup for MSW interceptor (cookies might be blocked in some browser security settings)
let inMemorySession: string | null = null;

const MOCK_USERS = {
  ADMIN: {
    id: "usr_admin_1",
    email: "admin@parcelflow.com",
    fullName: "Alex Rivera",
    role: "ADMIN" as const,
  },
  HUB_STAFF: {
    id: "usr_hub_1",
    email: "hub@parcelflow.com",
    fullName: "Marcus Chen",
    role: "HUB_STAFF" as const,
    hubId: "hub_hanoi_01",
  },
  DISPATCHER: {
    id: "usr_disp_1",
    email: "dispatcher@parcelflow.com",
    fullName: "Sarah Jenkins",
    role: "DISPATCHER" as const,
  },
  SHIPPER: {
    id: "usr_ship_1",
    email: "shipper@parcelflow.com",
    fullName: "David Beckham",
    role: "SHIPPER" as const,
    phoneNumber: "0901234567",
  },
};

function getMockUserByEmail(email: string) {
  const normalized = email.toLowerCase().trim();
  if (normalized.startsWith("hub")) return MOCK_USERS.HUB_STAFF;
  if (normalized.startsWith("disp")) return MOCK_USERS.DISPATCHER;
  if (normalized.startsWith("ship")) return MOCK_USERS.SHIPPER;
  return MOCK_USERS.ADMIN;
}

export const handlers = [
  // Mock login handler
  http.post(apiPath("/auth/login"), async ({ request }) => {
    try {
      const body = (await request.json()) as { email: string; password?: string };
      const user = getMockUserByEmail(body.email);
      
      inMemorySession = user.role;

      return HttpResponse.json(
        {
          success: true,
          message: "Login successful (mocked)",
          data: { user },
          timestamp: new Date().toISOString(),
        },
        {
          headers: {
            "Set-Cookie": `pf_role=${user.role}; Path=/; SameSite=Lax; Max-Age=3600`,
          },
        }
      );
    } catch {
      return HttpResponse.json(
        { success: false, message: "Invalid request body" },
        { status: 400 }
      );
    }
  }),

  // Mock get profile handler
  http.get(apiPath("/auth/me"), ({ request }) => {
    // Read cookie header
    const cookieHeader = request.headers.get("Cookie") || "";
    const roleMatch = cookieHeader.match(/pf_role=([^;]+)/);
    const role = roleMatch ? roleMatch[1] : inMemorySession;

    if (!role || !Object.keys(MOCK_USERS).includes(role)) {
      return HttpResponse.json(
        {
          success: false,
          message: "Unauthorized - No active mock session",
          timestamp: new Date().toISOString(),
        },
        { status: 401 }
      );
    }

    const user = MOCK_USERS[role as keyof typeof MOCK_USERS];

    return HttpResponse.json({
      success: true,
      data: { user },
      timestamp: new Date().toISOString(),
    });
  }),

  // Mock logout handler
  http.post(apiPath("/auth/logout"), () => {
    inMemorySession = null;
    return HttpResponse.json(
      {
        success: true,
        message: "Logged out successfully (mocked)",
        timestamp: new Date().toISOString(),
      },
      {
        headers: {
          "Set-Cookie": "pf_role=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
        },
      }
    );
  }),

  // Mock tracking search handler
  http.get(apiPath("/tracking/:code"), ({ params }) => {
    const code = String(params.code).toUpperCase();
    
    const mockTrackings: Record<string, OrderTrackingInfo> = {
      "ORD-1002495": {
        orderCode: "ORD-1002495",
        status: "OUT_FOR_DELIVERY",
        senderName: "Nguyen Van A",
        senderAddress: "85 Nguyen Chi Thanh, Hanoi",
        receiverName: "Tran Thi B",
        receiverAddress: "123 Le Loi, District 1, Ho Chi Minh City",
        phoneNumber: "0901234567",
        parcels: [
          { parcelCode: "PCL-1002495-01", weight: 1.5, description: "Electronic components" },
          { parcelCode: "PCL-1002495-02", weight: 0.8, description: "Documentation binder" }
        ],
        events: [
          { id: "evt_4", status: "OUT_FOR_DELIVERY", description: "Courier is delivering the parcel", locationName: "Ho Chi Minh Gateway Hub", timestamp: "2026-06-12T14:30:00Z" },
          { id: "evt_3", status: "HUB_INBOUND", description: "Scanned in at local delivery hub", locationName: "Ho Chi Minh Gateway Hub", timestamp: "2026-06-12T08:15:00Z" },
          { id: "evt_2", status: "HUB_OUTBOUND", description: "Dispatched from Hanoi transit node", locationName: "Hanoi Central Hub", timestamp: "2026-06-11T20:00:00Z" },
          { id: "evt_1", status: "CREATED", description: "Logistics order created & registered", locationName: "Hanoi Central Hub", timestamp: "2026-06-11T10:00:00Z" }
        ]
      },
      "ORD-2003841": {
        orderCode: "ORD-2003841",
        status: "DELIVERED",
        senderName: "Le Hoang C",
        senderAddress: "456 Tran Hung Dao, Da Nang",
        receiverName: "Pham Minh D",
        receiverAddress: "789 Nguyen Hue, District 1, Ho Chi Minh City",
        phoneNumber: "0987654321",
        parcels: [
          { parcelCode: "PCL-2003841-01", weight: 3.2, description: "Premium coffee beans" }
        ],
        events: [
          { id: "evt_5", status: "DELIVERED", description: "Successfully handed over to consignee", locationName: "Ho Chi Minh Gateway Hub", timestamp: "2026-06-10T16:45:00Z" },
          { id: "evt_4", status: "OUT_FOR_DELIVERY", description: "Courier is delivering the parcel", locationName: "Ho Chi Minh Gateway Hub", timestamp: "2026-06-10T09:00:00Z" },
          { id: "evt_3", status: "HUB_INBOUND", description: "Scanned in at local delivery hub", locationName: "Ho Chi Minh Gateway Hub", timestamp: "2026-06-10T02:30:00Z" },
          { id: "evt_2", status: "HUB_OUTBOUND", description: "Dispatched from Da Nang transit node", locationName: "Da Nang Transit Hub", timestamp: "2026-06-09T14:00:00Z" },
          { id: "evt_1", status: "CREATED", description: "Logistics order created & registered", locationName: "Da Nang Transit Hub", timestamp: "2026-06-09T08:30:00Z" }
        ]
      },
      "ORD-3004812": {
        orderCode: "ORD-3004812",
        status: "CREATED",
        senderName: "Vu Minh E",
        senderAddress: "99 Cau Giay, Hanoi",
        receiverName: "Ngo Thanh F",
        receiverAddress: "12 Nguyen Van Linh, Da Nang",
        phoneNumber: "0912345678",
        parcels: [
          { parcelCode: "PCL-3004812-01", weight: 0.5, description: "Gift pack - Apparel" }
        ],
        events: [
          { id: "evt_1", status: "CREATED", description: "Logistics order created & registered", locationName: "Hanoi Central Hub", timestamp: "2026-06-12T17:00:00Z" }
        ]
      }
    };

    const tracking = mockTrackings[code];

    if (!tracking) {
      return HttpResponse.json(
        {
          success: false,
          message: `Order code "${code}" not found. Verify the input code is correct.`,
        },
        { status: 404 }
      );
    }

    return HttpResponse.json({
      success: true,
      data: { tracking },
      timestamp: new Date().toISOString(),
    });
  }),
];

