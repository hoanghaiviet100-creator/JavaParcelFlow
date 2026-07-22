export const SHIPPER_ROUTES = {
  assignments: "/shipper/assignments",
  assignmentDetail: (id: string) => `/shipper/assignments/${id}`,
} as const;
