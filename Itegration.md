# API Documentation & Frontend Architecture — Asset Management System

**Version:** 1.0 | **Base URL:** `http://localhost:8080` | **Auth:** Bearer JWT

---

## Mục lục

1. [Quy ước chung](#1-quy-ước-chung)
2. [Authentication](#2-authentication)
3. [Users](#3-users)
4. [Departments](#4-departments)
5. [Assets](#5-assets)
6. [Asset Assignments](#6-asset-assignments)
7. [Validation Sessions](#7-validation-sessions)
8. [Validation Records](#8-validation-records)
9. [Dashboard](#9-dashboard)
10. [Audit Logs](#10-audit-logs)
11. [Đề xuất kiến trúc Frontend](#11-đề xuất-kiến-trúc-frontend)

---

## 1. Quy ước chung

### 1.1 Response wrapper

Mọi response đều được bọc trong `ApiResponse<T>`:

```json
{
  "status": 200,
  "message": "Success",
  "data": { }
}
```

| Field | Type | Mô tả |
|---|---|---|
| `status` | `int` | HTTP status code |
| `message` | `string` | Mô tả kết quả |
| `data` | `T \| null` | Payload, `null` khi không có data |

### 1.2 Phân trang (`PageRes<T>`)

Các endpoint trả danh sách có filter đều dùng cấu trúc này trong field `data`:

```json
{
  "content": [ ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 128,
  "totalPages": 13
}
```

Query params phân trang: `page` (default `0`) · `size` (default `10`).

### 1.3 Enum values

| Enum | Các giá trị hợp lệ |
|---|---|
| `UserRole` | `admin` · `asset_manager` · `department_staff` · `auditor` |
| `AssetStatus` | `active` · `inactive` · `archived` · `disposed` |
| `AssetCategory` | `electronics` · `furniture` · `vehicle` · `equipment` · `software` · `other` |
| `ValidationSessionStatus` | `pending` · `in_progress` · `closed` |
| `ValidationRecordStatus` | `valid` · `invalid` · `missing` · `pending` |
| `AuditAction` | `asset_created` · `asset_updated` · `asset_archived` · `asset_deleted` · `asset_assigned` · `asset_transferred` · `validation_initiated` · `validation_record_updated` · `role_assigned` · `role_revoked` · `user_created` · `user_deactivated` |

### 1.4 Bảng lỗi chung

| HTTP | Tình huống | `message` mẫu |
|---|---|---|
| `400` | Validation thất bại | `"Asset name is required"` |
| `400` | Vi phạm business rule | `"Asset is already archived"` |
| `401` | Không có / sai / hết hạn token | `"Token not found"` |
| `401` | Sai email hoặc password | `"Invalid email or password"` |
| `401` | Account bị khoá | `"Account is deactivated"` |
| `403` | Không đủ quyền | `"Access Denied"` |
| `404` | Resource không tồn tại | `"Asset not found with id: <uuid>"` |
| `500` | Lỗi server | message từ exception |

---

## 2. Authentication

### 2.1 Đăng nhập

```
POST /api/auth/login
```

**Quyền:** Public

**Request body:**

```json
{
  "email": "alice@company.com",
  "password": "123"
}
```

| Field | Type | Bắt buộc | Validation |
|---|---|:---:|---|
| `email` | `string` | ✅ | Định dạng email hợp lệ |
| `password` | `string` | ✅ | Không được rỗng |

**Response `200`:**

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": "aaaaaaaa-0000-0000-0000-000000000001",
      "email": "alice@company.com",
      "fullName": "Alice Johnson",
      "role": "admin"
    }
  }
}
```

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"Email is required"` | Thiếu field `email` |
| `400` | `"Invalid email format"` | Email sai định dạng |
| `400` | `"Password is required"` | Thiếu field `password` |
| `400` | `"Account is deactivated"` | User bị lock (`is_active = false`) |
| `401` | `"Invalid email or password"` | Email không tồn tại hoặc sai password |

---

### 2.2 Refresh token

```
POST /api/auth/refresh
```

**Quyền:** Public  
**Header:** `X-Refresh-Token: <refreshToken>`

**Response `200`:** Cấu trúc giống Login — trả cặp `accessToken` + `refreshToken` mới.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"Invalid or expired refresh token"` | Token không hợp lệ hoặc hết hạn |
| `404` | `"User not found with id: <uuid>"` | User đã bị xoá khỏi DB |

---

### 2.3 Đăng xuất

```
POST /api/auth/logout
```

**Quyền:** Authenticated  
**Header:** `Authorization: Bearer <accessToken>`

**Response `200`:**

```json
{
  "status": 200,
  "message": "Logged out successfully",
  "data": null
}
```

> **Lưu ý:** Hiện tại JWT là stateless — logout phía client bằng cách xoá token khỏi storage. Mở rộng: tích hợp Redis blacklist.

---

## 3. Users

### 3.1 Thông tin bản thân

```
GET /api/users/me
```

**Quyền:** Authenticated (mọi role)

**Response `200`:**

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "id": "aaaaaaaa-0000-0000-0000-000000000001",
    "email": "alice@company.com",
    "fullName": "Alice Johnson",
    "role": "admin",
    "departmentId": "11111111-0000-0000-0000-000000000001",
    "departmentName": "Information Technology",
    "isActive": true,
    "createdAt": "2024-01-01T00:00:00Z"
  }
}
```

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `401` | `"Token not found"` | Không có / sai token |
| `404` | `"User not found with id: <uuid>"` | Token hợp lệ nhưng user đã bị xoá |

---

### 3.2 Danh sách users

```
GET /api/users?role=&isActive=&search=&page=0&size=10
```

**Quyền:** `admin`

**Query params:**

| Param | Type | Mô tả |
|---|---|---|
| `role` | `UserRole` | Lọc theo role (optional) |
| `isActive` | `boolean` | Lọc active/locked (optional) |
| `search` | `string` | Tìm theo `fullName` hoặc `email` (optional) |
| `page` | `int` | Trang, default `0` |
| `size` | `int` | Kích thước trang, default `10` |

**Response `200`:** `PageRes<UserRes>` — mỗi phần tử có cấu trúc giống `/me`.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `401` | `"Token not found"` | Không có token |
| `403` | `"Access Denied"` | Role không phải `admin` |

---

### 3.3 Chi tiết user

```
GET /api/users/{id}
```

**Quyền:** `admin`

**Response `200`:** Cấu trúc `UserRes` như `/me`.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `403` | `"Access Denied"` | Không phải admin |
| `404` | `"User not found with id: <uuid>"` | ID không tồn tại |

---

### 3.4 Gán role

```
PUT /api/users/{id}/role
```

**Quyền:** `admin`

**Request body:**

```json
{
  "role": "asset_manager"
}
```

| Field | Type | Bắt buộc | Validation |
|---|---|:---:|---|
| `role` | `UserRole` | ✅ | Một trong các giá trị enum hợp lệ |

**Response `200`:**

```json
{
  "status": 200,
  "message": "Role updated",
  "data": null
}
```

**Side effect:** Ghi `audit_logs` với `action = role_assigned`, `before_state = {"role": "<old>"}`, `after_state = {"role": "<new>"}`.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"Role is required"` | Body thiếu field `role` |
| `403` | `"Access Denied"` | Không phải admin |
| `404` | `"User not found with id: <uuid>"` | ID không tồn tại |

---

### 3.5 Khoá / mở khoá tài khoản

```
PUT /api/users/{id}/status
```

**Quyền:** `admin`

**Request body:**

```json
{
  "isActive": false
}
```

**Response `200`:**

```json
{
  "status": 200,
  "message": "Status updated",
  "data": null
}
```

**Side effect:** Ghi `audit_logs` với `action = user_deactivated` hoặc `user_created`.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"isActive is required"` | Thiếu field |
| `403` | `"Access Denied"` | Không phải admin |
| `404` | `"User not found with id: <uuid>"` | ID không tồn tại |

---

## 4. Departments

### 4.1 Danh sách phòng ban

```
GET /api/departments
```

**Quyền:** Authenticated (mọi role)

**Response `200`:**

```json
{
  "status": 200,
  "message": "Success",
  "data": [
    {
      "id": "11111111-0000-0000-0000-000000000001",
      "name": "Information Technology",
      "code": "IT"
    }
  ]
}
```

> Trả `List` (không phân trang), sắp xếp tăng dần theo `name`. Dùng cho dropdown.

---

### 4.2 Chi tiết phòng ban

```
GET /api/departments/{id}
```

**Quyền:** Authenticated (mọi role)

**Response `200`:** Cấu trúc `DepartmentRes` như trên.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `404` | `"Department not found with id: <uuid>"` | ID không tồn tại |

---

### 4.3 Tạo phòng ban

```
POST /api/departments
```

**Quyền:** `admin`

**Request body:**

```json
{
  "name": "Marketing",
  "code": "MKT"
}
```

| Field | Type | Bắt buộc | Validation |
|---|---|:---:|---|
| `name` | `string` | ✅ | Không rỗng, tối đa 100 ký tự |
| `code` | `string` | ✅ | Không rỗng, tối đa 20 ký tự, unique |

**Response `201`:** `DepartmentRes` vừa được tạo.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"Department name is required"` | Thiếu `name` |
| `400` | `"Department code already exists: MKT"` | `code` đã tồn tại |
| `403` | `"Access Denied"` | Không phải admin |

---

### 4.4 Cập nhật phòng ban

```
PUT /api/departments/{id}
```

**Quyền:** `admin`

**Request body:** Giống tạo mới.

**Response `200`:** `DepartmentRes` sau khi cập nhật.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `403` | `"Access Denied"` | Không phải admin |
| `404` | `"Department not found with id: <uuid>"` | ID không tồn tại |

---

## 5. Assets

### 5.1 Danh sách tài sản

```
GET /api/assets?departmentId=&status=&category=&search=&page=0&size=10
```

**Quyền:** Authenticated (mọi role)

**Query params:**

| Param | Type | Mô tả |
|---|---|---|
| `departmentId` | `UUID` | Lọc theo phòng ban (optional) |
| `status` | `AssetStatus` | Lọc theo trạng thái (optional) |
| `category` | `AssetCategory` | Lọc theo danh mục (optional) |
| `search` | `string` | Tìm theo `name` hoặc `assetCode` (optional) |

**Response `200`:** `PageRes<AssetRes>`

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": "cccccccc-0000-0000-0000-000000000001",
        "assetCode": "IT-2024-001",
        "name": "MacBook Pro 14\"",
        "description": "14-inch M3 Pro, 18GB RAM",
        "category": "electronics",
        "status": "active",
        "purchasePrice": 2499.00,
        "purchaseDate": "2024-01-15",
        "departmentId": "11111111-0000-0000-0000-000000000001",
        "departmentName": "Information Technology",
        "createdAt": "2024-01-15T08:00:00Z"
      }
    ],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 10,
    "totalPages": 1
  }
}
```

---

### 5.2 Chi tiết tài sản

```
GET /api/assets/{id}
```

**Quyền:** Authenticated (mọi role)

**Response `200`:** `AssetDetailRes` — đầy đủ hơn `AssetRes`, bao gồm thêm:

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "id": "cccccccc-0000-0000-0000-000000000001",
    "assetCode": "IT-2024-001",
    "name": "MacBook Pro 14\"",
    "description": "14-inch M3 Pro, 18GB RAM",
    "category": "electronics",
    "status": "active",
    "purchasePrice": 2499.00,
    "purchaseDate": "2024-01-15",
    "departmentId": "11111111-0000-0000-0000-000000000001",
    "departmentName": "Information Technology",
    "createdById": "aaaaaaaa-0000-0000-0000-000000000002",
    "createdByName": "Bob Smith",
    "archivedAt": null,
    "createdAt": "2024-01-15T08:00:00Z",
    "updatedAt": "2024-01-15T08:00:00Z"
  }
}
```

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `404` | `"Asset not found with id: <uuid>"` | ID không tồn tại |

---

### 5.3 Tạo tài sản

```
POST /api/assets
```

**Quyền:** `admin` · `asset_manager`

**Request body:**

```json
{
  "name": "Dell XPS 15",
  "description": "Laptop phòng IT",
  "category": "electronics",
  "status": "active",
  "purchasePrice": 1800.00,
  "purchaseDate": "2024-06-01",
  "departmentId": "11111111-0000-0000-0000-000000000001"
}
```

| Field | Type | Bắt buộc | Validation |
|---|---|:---:|---|
| `name` | `string` | ✅ | Không rỗng |
| `category` | `AssetCategory` | ✅ | Giá trị enum hợp lệ |
| `status` | `AssetStatus` | ✅ | Giá trị enum hợp lệ |
| `description` | `string` | ❌ | — |
| `purchasePrice` | `decimal` | ❌ | — |
| `purchaseDate` | `date` `yyyy-MM-dd` | ❌ | — |
| `departmentId` | `UUID` | ❌ | Phải tồn tại trong DB nếu có |

**Response `201`:**

```json
{
  "status": 201,
  "message": "Created",
  "data": "cccccccc-0000-0000-0000-000000000011"
}
```

> Trả UUID của asset vừa tạo. `assetCode` tự động sinh theo format `{DEPT_CODE}-{YEAR}-{SEQ}`.

**Side effect:** Ghi `audit_logs` với `action = asset_created`. Nếu có `departmentId`, tự động tạo `AssetAssignment` mở.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"Asset name is required"` | Thiếu `name` |
| `400` | `"Category is required"` | Thiếu `category` |
| `400` | `"Status is required"` | Thiếu `status` |
| `403` | `"Access Denied"` | Role không được phép |
| `404` | `"Department not found with id: <uuid>"` | `departmentId` không tồn tại |

---

### 5.4 Cập nhật tài sản

```
PUT /api/assets/{id}
```

**Quyền:** `admin` · `asset_manager`

**Request body:** Giống tạo mới (tất cả các field).

**Response `200`:**

```json
{
  "status": 200,
  "message": "Asset updated",
  "data": null
}
```

**Side effect:** Ghi `audit_logs` với `action = asset_updated`, `before_state` và `after_state` chứa `name` + `status`.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"Asset name is required"` | Thiếu `name` |
| `403` | `"Access Denied"` | Role không được phép |
| `404` | `"Asset not found with id: <uuid>"` | Asset không tồn tại |
| `404` | `"Department not found with id: <uuid>"` | `departmentId` mới không tồn tại |

---

### 5.5 Archive tài sản

```
PUT /api/assets/{id}/archive
```

**Quyền:** `admin` · `asset_manager`

**Response `200`:**

```json
{
  "status": 200,
  "message": "Asset archived",
  "data": null
}
```

**Side effect:** Set `status = archived`, `archived_at = now()`. Ghi `audit_logs` với `action = asset_archived`.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"Asset is already archived"` | Asset đã ở trạng thái `archived` |
| `403` | `"Access Denied"` | Role không được phép |
| `404` | `"Asset not found with id: <uuid>"` | Asset không tồn tại |

---

## 6. Asset Assignments

### 6.1 Chuyển phòng ban

```
POST /api/assets/{id}/transfer
```

**Quyền:** `admin` · `asset_manager`

**Request body:**

```json
{
  "newDepartmentId": "11111111-0000-0000-0000-000000000002",
  "notes": "Chuyển cho nhân viên mới phòng HR"
}
```

| Field | Type | Bắt buộc | Validation |
|---|---|:---:|---|
| `newDepartmentId` | `UUID` | ✅ | Phải tồn tại trong DB |
| `notes` | `string` | ❌ | — |

**Response `200`:**

```json
{
  "status": 200,
  "message": "Asset transferred",
  "data": null
}
```

**Side effect:**
- Assignment hiện tại được đóng: `returned_at = now()`
- Tạo assignment mới tới phòng ban đích
- Cập nhật `assets.current_department_id`
- Ghi `audit_logs` với `action = asset_transferred`

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"Target department is required"` | Thiếu `newDepartmentId` |
| `403` | `"Access Denied"` | Role không được phép |
| `404` | `"Asset not found with id: <uuid>"` | Asset không tồn tại |
| `404` | `"Department not found with id: <uuid>"` | Department đích không tồn tại |

---

### 6.2 Trả lại tài sản

```
PUT /api/assets/{id}/return
```

**Quyền:** `admin` · `asset_manager`

**Response `200`:**

```json
{
  "status": 200,
  "message": "Assignment closed",
  "data": null
}
```

**Side effect:** Set `returned_at = now()` trên assignment đang mở.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"No active assignment found for asset"` | Không có assignment nào đang mở |
| `403` | `"Access Denied"` | Role không được phép |
| `404` | `"Asset not found with id: <uuid>"` | Asset không tồn tại |

---

### 6.3 Lịch sử luân chuyển

```
GET /api/assets/{id}/history
```

**Quyền:** Authenticated (mọi role)

**Response `200`:**

```json
{
  "status": 200,
  "message": "Success",
  "data": [
    {
      "id": "uuid",
      "assetId": "cccccccc-0000-0000-0000-000000000001",
      "assetCode": "IT-2024-001",
      "assetName": "MacBook Pro 14\"",
      "departmentId": "11111111-0000-0000-0000-000000000002",
      "departmentName": "Human Resources",
      "assignedById": "aaaaaaaa-0000-0000-0000-000000000002",
      "assignedByName": "Bob Smith",
      "assignedAt": "2024-01-20T00:00:00Z",
      "returnedAt": "2024-01-25T00:00:00Z",
      "notes": "Mượn tạm HR, đã trả"
    }
  ]
}
```

> Sắp xếp mới nhất trước. `returnedAt = null` nghĩa là assignment đang mở.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `404` | `"Asset not found with id: <uuid>"` | Asset không tồn tại |

---

## 7. Validation Sessions

### 7.1 Danh sách sessions

```
GET /api/validation/sessions
```

**Quyền:** `admin` · `auditor`

**Response `200`:** `List<ValidationSessionRes>`

```json
{
  "status": 200,
  "message": "Success",
  "data": [
    {
      "id": "dddddddd-0000-0000-0000-000000000001",
      "year": 2024,
      "status": "in_progress",
      "initiatedById": "aaaaaaaa-0000-0000-0000-000000000007",
      "initiatedByName": "Grace Lee",
      "startedAt": "2024-12-01T00:00:00Z",
      "closedAt": null,
      "notes": null,
      "totalRecords": 10,
      "validCount": 6,
      "invalidCount": 1,
      "missingCount": 1,
      "pendingCount": 2
    }
  ]
}
```

---

### 7.2 Chi tiết session

```
GET /api/validation/sessions/{id}
```

**Quyền:** `admin` · `auditor`

**Response `200`:** `ValidationSessionRes` như trên.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `404` | `"ValidationSession not found with id: <uuid>"` | ID không tồn tại |

---

### 7.3 Mở session mới

```
POST /api/validation/sessions
```

**Quyền:** `admin` · `auditor`

**Request body:**

```json
{
  "year": 2025,
  "notes": "Kiểm kê định kỳ năm 2025"
}
```

| Field | Type | Bắt buộc | Validation |
|---|---|:---:|---|
| `year` | `short` | ✅ | Không null |
| `notes` | `string` | ❌ | — |

**Response `201`:** `ValidationSessionRes` vừa tạo.

**Side effect:** Tự động tạo `ValidationRecord` cho **tất cả** asset có `status = active`, với `status = pending`.

**Side effect:** Ghi `audit_logs` với `action = validation_initiated`.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"Year is required"` | Thiếu `year` |
| `400` | `"A validation session for year 2025 already exists"` | Session năm đó đã tồn tại |
| `400` | `"Another session is already in progress"` | Đang có session chưa đóng |
| `403` | `"Access Denied"` | Không phải admin / auditor |

---

### 7.4 Đóng session

```
PUT /api/validation/sessions/{id}/close
```

**Quyền:** `admin` · `auditor`

**Response `200`:**

```json
{
  "status": 200,
  "message": "Session closed",
  "data": null
}
```

**Side effect:** Set `status = closed`, `closed_at = now()`.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"Session is already closed"` | Session đã đóng trước đó |
| `403` | `"Access Denied"` | Không phải admin / auditor |
| `404` | `"ValidationSession not found with id: <uuid>"` | ID không tồn tại |

---

## 8. Validation Records

### 8.1 Danh sách records của session

```
GET /api/validation/sessions/{id}/records?status=&departmentId=
```

**Quyền:** Authenticated (mọi role)

**Query params:**

| Param | Type | Mô tả |
|---|---|---|
| `status` | `ValidationRecordStatus` | Lọc theo kết quả kiểm kê (optional) |
| `departmentId` | `UUID` | Lọc theo phòng ban của asset (optional) |

**Response `200`:** `List<ValidationRecordRes>`

```json
{
  "status": 200,
  "message": "Success",
  "data": [
    {
      "id": "uuid",
      "sessionId": "dddddddd-0000-0000-0000-000000000001",
      "assetId": "cccccccc-0000-0000-0000-000000000007",
      "assetCode": "LOG-2024-001",
      "assetName": "Pallet Jack",
      "departmentName": "Logistics",
      "status": "missing",
      "validatedById": "aaaaaaaa-0000-0000-0000-000000000005",
      "validatedByName": "Eve Davis",
      "validatedAt": "2024-12-05T10:00:00Z",
      "notes": "Không tìm thấy tại vị trí"
    }
  ]
}
```

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `404` | `"ValidationSession not found with id: <uuid>"` | Session không tồn tại |

---

### 8.2 Submit kết quả kiểm kê

```
PUT /api/validation/assets/{assetId}/status
PUT /api/assets/{assetId}/validation-status   ← alias
```

**Quyền:** `admin` · `department_staff`

**Request body:**

```json
{
  "status": "valid",
  "notes": "Máy hoạt động bình thường"
}
```

| Field | Type | Bắt buộc | Validation |
|---|---|:---:|---|
| `status` | `ValidationRecordStatus` | ✅ | Một trong `valid` · `invalid` · `missing` |
| `notes` | `string` | ❌ | — |

**Response `200`:**

```json
{
  "status": 200,
  "message": "Validation status updated",
  "data": null
}
```

**Side effect:** Cập nhật `ValidationRecord.status`, `validated_by`, `validated_at`. Ghi `audit_logs` với `action = validation_record_updated`.

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `400` | `"Status is required"` | Thiếu `status` |
| `400` | `"No active validation session or no record for this asset"` | Không có session đang chạy hoặc asset không có record |
| `403` | `"Access Denied"` | Role không được phép |

---

## 9. Dashboard

### 9.1 Thống kê tổng quan

```
GET /api/dashboard/stats
```

**Quyền:** Authenticated (mọi role)

**Response `200`:**

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "totalAssets": 10,
    "activeAssets": 7,
    "inactiveAssets": 1,
    "archivedAssets": 1,
    "disposedAssets": 0,
    "validationValid": 6,
    "validationInvalid": 1,
    "validationMissing": 1,
    "validationPending": 2
  }
}
```

> `validationXxx` lấy từ session đang `in_progress`. Nếu không có session active thì trả về `0`.

---

### 9.2 Thống kê theo phòng ban

```
GET /api/dashboard/by-department
```

**Quyền:** Authenticated (mọi role)

**Response `200`:**

```json
{
  "status": 200,
  "message": "Success",
  "data": [
    {
      "departmentId": "11111111-0000-0000-0000-000000000001",
      "departmentName": "Information Technology",
      "assetCount": 4,
      "totalValue": 11348.00
    }
  ]
}
```

---

### 9.3 Tiến độ kiểm kê hiện tại

```
GET /api/dashboard/validation-progress
```

**Quyền:** Authenticated (mọi role)

**Response `200`:** `ValidationSessionRes` của session đang `in_progress`, hoặc `null` nếu không có.

```json
{
  "status": 200,
  "message": "Success",
  "data": null
}
```

---

## 10. Audit Logs

### 10.1 Danh sách audit logs

```
GET /api/audit-logs?action=&assetId=&performedById=&from=&to=&page=0&size=20
```

**Quyền:** `admin` · `auditor`

**Query params:**

| Param | Type | Mô tả |
|---|---|---|
| `action` | `AuditAction` | Lọc theo loại hành động (optional) |
| `assetId` | `UUID` | Lọc theo tài sản (optional) |
| `performedById` | `UUID` | Lọc theo người thực hiện (optional) |
| `from` | `ISO-8601 datetime` | Từ thời điểm (optional) |
| `to` | `ISO-8601 datetime` | Đến thời điểm (optional) |

**Response `200`:** `PageRes<AuditLogRes>`, sắp xếp `created_at DESC`.

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": "uuid",
        "action": "asset_transferred",
        "performedById": "aaaaaaaa-0000-0000-0000-000000000002",
        "performedByName": "Bob Smith",
        "assetId": "cccccccc-0000-0000-0000-000000000001",
        "assetCode": "IT-2024-001",
        "targetUserId": null,
        "targetUserName": null,
        "beforeState": { "department": "HR" },
        "afterState": { "department": "IT" },
        "ipAddress": null,
        "createdAt": "2024-01-25T10:00:00Z"
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 8,
    "totalPages": 1
  }
}
```

**Exceptions:**

| Status | message | Điều kiện |
|---|---|---|
| `403` | `"Access Denied"` | Role không phải admin / auditor |

---

### 10.2 Audit logs theo asset

```
GET /api/audit-logs/assets/{assetId}
```

**Quyền:** `admin` · `auditor`

**Response `200`:** `List<AuditLogRes>` — tất cả logs của asset đó, sắp xếp mới nhất trước (không phân trang).

---

## 11. Đề xuất kiến trúc Frontend

### 11.1 Tech stack

| Lớp | Lựa chọn đề xuất | Lý do |
|---|---|---|
| Framework | **React 18 + TypeScript** | Ecosystem lớn, type-safe, phù hợp SPA phức tạp |
| Build tool | **Vite** | Nhanh hơn CRA, HMR tốt |
| UI Library | **Ant Design 5** hoặc **shadcn/ui** | Sẵn Table, Form, Modal phù hợp admin dashboard |
| State management | **Zustand** | Nhẹ, đủ cho auth state + global cache |
| Server state | **TanStack Query v5** | Cache, refetch, loading/error tự động cho API calls |
| Routing | **React Router v6** | Nested routes, lazy loading |
| HTTP Client | **Axios** | Interceptor tiện cho JWT attach + 401 redirect |
| Form | **React Hook Form + Zod** | Validation schema-first, ít re-render |
| Charts | **Recharts** | Nhẹ, đủ cho Dashboard KPI |

---

### 11.2 Cấu trúc thư mục

```
src/
├── api/                        # Axios instances & API functions
│   ├── axiosClient.ts          # Base instance, interceptors
│   ├── auth.api.ts
│   ├── users.api.ts
│   ├── assets.api.ts
│   ├── departments.api.ts
│   ├── validation.api.ts
│   ├── dashboard.api.ts
│   └── auditLogs.api.ts
│
├── components/                 # Shared UI components
│   ├── common/
│   │   ├── PageHeader.tsx
│   │   ├── DataTable.tsx       # Wrapper Ant Design Table + pagination
│   │   ├── StatusBadge.tsx     # Hiển thị AssetStatus, RecordStatus
│   │   └── ConfirmModal.tsx
│   └── layout/
│       ├── AppLayout.tsx       # Sidebar + Header + Outlet
│       ├── Sidebar.tsx         # Nav items theo role
│       └── PrivateRoute.tsx    # Guard check auth + role
│
├── features/                   # Feature-based modules
│   ├── auth/
│   │   ├── LoginPage.tsx
│   │   └── useAuthStore.ts     # Zustand: token, user, login(), logout()
│   │
│   ├── users/
│   │   ├── UserListPage.tsx
│   │   ├── UserDetailModal.tsx
│   │   └── AssignRoleModal.tsx
│   │
│   ├── departments/
│   │   ├── DepartmentListPage.tsx
│   │   └── DepartmentFormModal.tsx
│   │
│   ├── assets/
│   │   ├── AssetListPage.tsx
│   │   ├── AssetDetailPage.tsx
│   │   ├── AssetFormModal.tsx   # Dùng chung cho create & update
│   │   ├── TransferModal.tsx
│   │   └── AssignmentHistory.tsx
│   │
│   ├── validation/
│   │   ├── SessionListPage.tsx
│   │   ├── SessionDetailPage.tsx
│   │   ├── RecordTable.tsx
│   │   └── SubmitStatusModal.tsx
│   │
│   ├── dashboard/
│   │   ├── DashboardPage.tsx
│   │   ├── KpiCards.tsx
│   │   ├── DepartmentChart.tsx
│   │   └── ValidationProgress.tsx
│   │
│   └── auditLogs/
│       └── AuditLogPage.tsx
│
├── hooks/                      # Shared custom hooks
│   ├── useCurrentUser.ts
│   └── usePermission.ts        # hasRole(), canDo()
│
├── types/                      # TypeScript types, mirrors backend DTOs
│   ├── api.types.ts            # ApiResponse<T>, PageRes<T>
│   ├── user.types.ts
│   ├── asset.types.ts
│   ├── validation.types.ts
│   └── enums.ts
│
├── router/
│   └── index.tsx               # All routes definition
│
└── utils/
    ├── token.ts                # get/set/remove token từ localStorage
    └── formatters.ts           # formatDate, formatCurrency, formatEnum
```

---

### 11.3 Axios client & JWT interceptor

```typescript
// api/axiosClient.ts
const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  headers: { 'Content-Type': 'application/json' },
});

// Attach token tự động
axiosClient.interceptors.request.use((config) => {
  const token = tokenUtils.getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Xử lý 401 tập trung
axiosClient.interceptors.response.use(
  (res) => res,
  async (error) => {
    if (error.response?.status === 401) {
      tokenUtils.clear();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

---

### 11.4 Role-based routing

```typescript
// router/index.tsx
const routes = [
  { path: '/login',      element: <LoginPage /> },
  {
    element: <PrivateRoute />,        // Kiểm tra có token
    children: [
      { path: '/', element: <AppLayout />, children: [
        { path: 'dashboard',    element: <DashboardPage /> },
        { path: 'assets',       element: <AssetListPage /> },
        { path: 'assets/:id',   element: <AssetDetailPage /> },

        // Admin only
        { path: 'users',        element: <RoleRoute roles={['admin']}><UserListPage /></RoleRoute> },
        { path: 'departments',  element: <RoleRoute roles={['admin']}><DepartmentListPage /></RoleRoute> },
        { path: 'audit-logs',   element: <RoleRoute roles={['admin','auditor']}><AuditLogPage /></RoleRoute> },

        // Admin + Auditor
        { path: 'validation',   element: <RoleRoute roles={['admin','auditor','department_staff']}><SessionListPage /></RoleRoute> },
      ]},
    ],
  },
];
```

---

### 11.5 Pages theo role

| Role | Pages có thể truy cập |
|---|---|
| `admin` | Dashboard · Assets · Users · Departments · Validation · Audit Logs |
| `asset_manager` | Dashboard · Assets (tạo, sửa, chuyển) |
| `department_staff` | Dashboard · Assets (xem) · Validation (submit kết quả) |
| `auditor` | Dashboard · Assets (xem) · Validation (mở/đóng session, xem records) · Audit Logs |

---

### 11.6 Quản lý state với TanStack Query

```typescript
// features/assets/useAssets.ts
export const useAssets = (filters: AssetFilters) =>
  useQuery({
    queryKey: ['assets', filters],
    queryFn: () => assetsApi.getAll(filters),
  });

export const useCreateAsset = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: assetsApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      message.success('Tài sản đã được tạo');
    },
    onError: (err: ApiError) => {
      message.error(err.response?.data?.message ?? 'Có lỗi xảy ra');
    },
  });
};
```

---

### 11.7 TypeScript types (mirrors backend DTOs)

```typescript
// types/api.types.ts
export interface ApiResponse<T> {
  status: number;
  message: string;
  data: T | null;
}

export interface PageRes<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

// types/enums.ts
export type UserRole = 'admin' | 'asset_manager' | 'department_staff' | 'auditor';
export type AssetStatus = 'active' | 'inactive' | 'archived' | 'disposed';
export type AssetCategory = 'electronics' | 'furniture' | 'vehicle' | 'equipment' | 'software' | 'other';
export type ValidationRecordStatus = 'valid' | 'invalid' | 'missing' | 'pending';

// types/asset.types.ts
export interface AssetRes {
  id: string;
  assetCode: string;
  name: string;
  description: string | null;
  category: AssetCategory;
  status: AssetStatus;
  purchasePrice: number | null;
  purchaseDate: string | null;
  departmentId: string | null;
  departmentName: string | null;
  createdAt: string;
}
```