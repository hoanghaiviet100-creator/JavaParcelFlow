import { ApiError as IApiError } from "@/types/api/response";

export class ApiError extends Error {
  public status: number;
  public success: false;
  public code?: string;
  public details?: Record<string, string[]>;
  public timestamp: string;

  constructor(status: number, data: Partial<IApiError> & { message: string }) {
    super(data.message);
    this.name = "ApiError";
    this.status = status;
    this.success = false;
    this.code = data.code;
    this.details = data.details;
    this.timestamp = data.timestamp ?? new Date().toISOString();
  }
}
