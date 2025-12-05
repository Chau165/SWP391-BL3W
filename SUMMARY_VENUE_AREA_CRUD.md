TÓM TẮT THAY ĐỔI - TÍNH NĂNG CRUD CHO VENUE & VENUE_AREA

1) Mục tiêu
- Hoàn thiện CRUD cho `Venue` và `Venue_Area` (thêm PUT/UPDATE và DELETE soft-delete cho Venue_Area).
- Cho phép cả 2 role `ADMIN` và `ORGANIZER` thao tác các chức năng CRUD và xem danh sách.
- Cập nhật OpenAPI (`web/api/openapi.json`) để phản ánh các endpoint mới.

2) Các file đã chỉnh sửa / thêm
- Đã chỉnh sửa (logic):
  - `src/java/service/VenueService.java` - Cho phép `ADMIN` hoặc `ORGANIZER` tạo, cập nhật, xóa mềm (`soft-delete`) venue.
  - `src/java/service/VenueAreaService.java` - Cho phép `ADMIN` hoặc `ORGANIZER` tạo, cập nhật, xóa mềm area; thêm kiểm tra tồn tại `areaExists`.
  - `src/java/DAO/VenueAreaDAO.java` - Thêm method `areaExists(int areaId)`; giữ nguyên các method `createArea`, `updateArea`, `softDeleteArea`, `getAvailableAreasByVenueId`...
  - `src/java/controller/VenueAreaController.java` - Controller đã có `GET`, `POST`, `PUT`, `DELETE` (không thay đổi nhiều, đã sử dụng service mới).

- Đã cập nhật tài liệu API (OpenAPI):
  - `web/api/openapi.json` (và `src/main/webapp/api/openapi.json`) - Đã thêm `PUT` và `DELETE` cho `/api/venues/areas`, đảm bảo toàn bộ JSON hợp lệ; tất cả `example` đã theo quy tắc: string -> "string", integer -> 0, date-time -> "2024-01-01T00:00:00".

- Đã xóa (theo yêu cầu cleanup):
  - `README.md` (project root)
  - `BackEnd/README.md`
  - `BackEnd/FPTEventManagement/web/swagger-ui/README.md`
  - `BackEnd/FPTEventManagement/db/update_password_hashes.sql`

3) Lý do xóa
- Các file README phụ này không cần thiết cho chức năng hệ thống và có thể gây nhầm lẫn; user đã xác nhận xóa.
- `update_password_hashes.sql` là script rời, không phải phần của luồng deploy/kịch bản hiện tại; nếu cần, có thể khôi phục từ lịch sử git.

4) Cách chạy project (HƯỚNG DẪN TIẾN HÀNH TRÊN Windows - PowerShell)

Giả sử bạn đang ở máy dev Windows và đã cài Java (JDK 11+), Ant, và Tomcat. Thực hiện các bước sau:

Bước 0 - Môi trường yêu cầu
- JDK 11+ (JAVA_HOME trỏ đến thư mục JDK)
- Apache Ant (đã cài vào PATH)
- Tomcat (đã cấu hình và chạy)
- SQL Server (cấu hình kết nối trong `mylib/DBUtils`)

Bước 1 - Build project
```powershell
cd 'c:\AK\HOCKI5\SWP391 (Relearn)\Branch_Chau\BackEnd\FPTEventManagement'
ant clean
ant
```
- `ant` sẽ tạo WAR theo `build.xml`/`nbproject` cấu hình.

Bước 2 - Triển khai WAR lên Tomcat
- Copy file WAR từ `dist` hoặc `build` (tùy cấu hình ant) vào `webapps` của Tomcat hoặc dùng manager để deploy.

Bước 3 - Khởi động/Restart Tomcat
- Restart Tomcat nếu cần; mặc định app sẽ chạy tại `http://localhost:8080/FPTEventManagement`.

Bước 4 - Kiểm tra OpenAPI & Swagger UI
- Kiểm tra raw OpenAPI: `http://localhost:8080/FPTEventManagement/api/openapi.json`
- Mở Swagger UI: `http://localhost:8080/FPTEventManagement/swagger-ui/index.html` (hoặc tương ứng)
- Kiểm tra endpoints dưới tag `Venue`:
  - `GET /api/venues` - lấy danh sách venues
  - `POST /api/venues` - tạo venue (ADMIN|ORGANIZER)
  - `PUT /api/venues` - cập nhật venue (ADMIN|ORGANIZER)
  - `DELETE /api/venues?venueId=...` - soft-delete venue (ADMIN|ORGANIZER)
  - `GET /api/venues/areas?venueId=...` - lấy danh sách areas (ADMIN|ORGANIZER)
  - `POST /api/venues/areas` - tạo area (ADMIN|ORGANIZER)
  - `PUT /api/venues/areas` - cập nhật area (ADMIN|ORGANIZER)
  - `DELETE /api/venues/areas?areaId=...` - soft-delete area (ADMIN|ORGANIZER)

Bước 5 - Test thực tế (ví dụ dùng PowerShell Invoke-RestMethod)
- Gọi API GET venues (cần token nếu endpoint bảo mật):
```powershell
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/FPTEventManagement/api/venues' -Headers @{ Authorization = 'Bearer <TOKEN>' }
```
- Tạo area (Admin/Organizer token):
```powershell
$body = @{ venueId = 1; areaName = 'string'; floor = 'string'; capacity = 10 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/FPTEventManagement/api/venues/areas' -Body $body -ContentType 'application/json' -Headers @{ Authorization = 'Bearer <TOKEN>' }
```
- Update area:
```powershell
$body = @{ areaId = 5; areaName = 'string'; floor = 'string'; capacity = 20 } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri 'http://localhost:8080/FPTEventManagement/api/venues/areas' -Body $body -ContentType 'application/json' -Headers @{ Authorization = 'Bearer <TOKEN>' }
```
- Delete area (soft-delete):
```powershell
Invoke-RestMethod -Method Delete -Uri 'http://localhost:8080/FPTEventManagement/api/venues/areas?areaId=5' -Headers @{ Authorization = 'Bearer <TOKEN>' }
```

5) Mapping mã nguồn (chi tiết file -> chức năng)
- Controller:
  - `src/java/controller/VenueController.java` - endpoints cho `api/venues` (GET, POST, PUT, DELETE)
  - `src/java/controller/VenueAreaController.java` - endpoints cho `api/venues/areas` (GET, POST, PUT, DELETE)

- Service:
  - `src/java/service/VenueService.java` - logic nghiệp vụ cho Venue (role checks: ADMIN|ORGANIZER)
  - `src/java/service/VenueAreaService.java` - logic nghiệp vụ cho VenueArea (role checks: ADMIN|ORGANIZER; validation; area exist check)

- DAO:
  - `src/java/DAO/VenueDAO.java` - tương tác DB cho Venue (get, create, update, soft delete)
  - `src/java/DAO/VenueAreaDAO.java` - tương tác DB cho Venue_Area (getAvailable, create, update, softDelete, areaExists)

- DTOs:
  - `src/java/DTO/VenueDTO.java` - định nghĩa dữ liệu cho Venue
  - `src/java/DTO/VenueArea.java` - định nghĩa dữ liệu cho VenueArea

- OpenAPI / Swagger:
  - `web/api/openapi.json` và `src/main/webapp/api/openapi.json` - file OpenAPI JSON đã cập nhật (PUT/DELETE cho /api/venues/areas)

6) Git / Branch / Commit
- Tôi đã áp dụng thay đổi trong workspace. Nếu bạn muốn, tôi có thể tạo branch và commit cục bộ; để push lên GitHub bạn cần cung cấp quyền hoặc thực hiện lệnh push từ máy của bạn.

Các lệnh Git bạn có thể chạy (PowerShell):
```powershell
cd 'c:\AK\HOCKI5\SWP391 (Relearn)\Branch_Chau'
# Tạo branch mới
git checkout -b feature/venue-area-crud
# Kiểm tra thay đổi
git status
# Thêm thay đổi
git add .
# Commit
git commit -m "Feature: Add PUT/DELETE for Venue_Area; allow ADMIN|ORGANIZER for venue and area CRUD; update OpenAPI"
# Push lên remote
git push -u origin feature/venue-area-crud
```

7) Ghi chú cuối
- Nếu bạn muốn tôi tự động tạo branch và commit trong repo này từ môi trường hiện tại, tôi có thể thử tạo branch và commit cục bộ; nhưng để push lên GitHub, tôi cần quyền credentials (thường do bạn thực hiện từ máy dev).
- Nếu cần khôi phục bất kỳ file README hoặc SQL nào, có thể lấy lại từ git history trước khi xóa.

---
Nếu đồng ý, tôi sẽ tiếp tục: (A) tạo branch `feature/venue-area-crud` và commit các thay đổi cục bộ, hoặc (B) chỉ tạo các lệnh git để bạn chạy và thực hiện push. Vui lòng chọn phương án.