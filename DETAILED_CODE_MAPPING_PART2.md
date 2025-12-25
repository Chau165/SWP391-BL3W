# 📚 HƯỚNG DẪN CHI TIẾT - PHẦN 2

> **Tài liệu này tiếp tục giải thích chi tiết các chức năng còn lại**

---

## 6. EVENT STATISTICS

### 📝 Mô tả
Thống kê chi tiết về sự kiện: tổng số đăng ký, check-in, check-out, tỉ lệ attendance.

### 🔗 Luồng xử lý chi tiết

```
1. Frontend (ORGANIZER/STAFF Dashboard)
   ↓
   📤 GET /api/events/stats?eventId=123
   📤 Header: Authorization: Bearer <JWT_TOKEN>
   ↓

2. controller/EventStatsController.java (doGet)
   
   ✅ Bước 1: Extract JWT token
      - String authHeader = request.getHeader("Authorization")
      - if (!authHeader.startsWith("Bearer ")) → 401
      - String token = authHeader.substring(7)
   
   ✅ Bước 2: Validate JWT token
      - JwtUtils.validateToken(token)
      
      Validate logic:
      a) Parse token thành Claims
         Claims claims = Jwts.parser()
           .setSigningKey(SECRET_KEY)
           .parseClaimsJws(token)
           .getBody();
      
      b) Kiểm tra expiration
         Date expiration = claims.getExpiration();
         if (expiration.before(new Date())) {
           return false;  // Token hết hạn
         }
      
      c) Return true nếu valid
      
      - Nếu invalid → 401 Unauthorized
   
   ✅ Bước 3: Kiểm tra role
      - JwtUtils.getRoleFromToken(token)
      
      Logic:
      Claims claims = parseToken(token);
      String role = claims.get("role", String.class);
      return role;
      
      - Chỉ cho phép: ORGANIZER, STAFF, ADMIN
      - Nếu role khác → 403 Forbidden
   
   ✅ Bước 4: Parse eventId từ query parameter
      - String eventIdStr = request.getParameter("eventId")
      - int eventId = Integer.parseInt(eventIdStr)
      
      - Nếu thiếu eventId → 400 Bad Request
      - Nếu không parse được → 400 Bad Request
   
   ✅ Bước 5: Lấy thống kê từ database
      - TicketDAO.getEventStats(eventId)
      
      SQL Query (Complex):
      
      WITH TicketCounts AS (
        SELECT 
          event_id,
          COUNT(*) as total_registered,
          SUM(CASE WHEN status = 'CHECKED_IN' THEN 1 ELSE 0 END) as total_checked_in,
          SUM(CASE WHEN status = 'CHECKED_OUT' THEN 1 ELSE 0 END) as total_checked_out,
          SUM(CASE WHEN status = 'BOOKED' THEN 1 ELSE 0 END) as total_booked,
          SUM(CASE WHEN status = 'REFUNDED' THEN 1 ELSE 0 END) as total_refunded
        FROM Ticket
        WHERE event_id = ?
        GROUP BY event_id
      )
      SELECT 
        tc.event_id,
        tc.total_registered,
        tc.total_checked_in,
        tc.total_checked_out,
        tc.total_booked,
        tc.total_refunded,
        CAST(tc.total_checked_in * 100.0 / NULLIF(tc.total_registered, 0) AS DECIMAL(5,2)) as check_in_rate,
        CAST(tc.total_checked_out * 100.0 / NULLIF(tc.total_registered, 0) AS DECIMAL(5,2)) as check_out_rate
      FROM TicketCounts tc
      
      Giải thích query:
      
      1. CTE TicketCounts: Đếm tickets theo status
         - COUNT(*): Tổng số vé
         - SUM(CASE WHEN...): Đếm có điều kiện
      
      2. Main query: Tính tỉ lệ phần trăm
         - check_in_rate = (checked_in / total) * 100
         - check_out_rate = (checked_out / total) * 100
         - NULLIF(tc.total_registered, 0): Tránh chia cho 0
      
      Return: EventStatsResponse object
   
   ✅ Bước 6: Kiểm tra kết quả
      - if (stats == null) → 404 Not Found
      
      Trường hợp null:
      - Event không tồn tại
      - Event chưa có ticket nào
   
   ✅ Bước 7: Trả response
      - Status: 200 OK
      - Body: {
          "eventId": 123,
          "totalRegistered": 500,
          "totalCheckedIn": 350,
          "totalCheckedOut": 200,
          "totalBooked": 150,
          "totalRefunded": 50,
          "checkInRate": 70.0,
          "checkOutRate": 40.0
        }
```

### 🗂️ Database Schema

```sql
-- Bảng Ticket (lưu thông tin vé)
CREATE TABLE Ticket (
    ticket_id INT PRIMARY KEY IDENTITY(1,1),
    event_id INT NOT NULL,                    -- FK → Event
    user_id INT NOT NULL,                     -- FK → Users
    category_ticket_id INT,                   -- FK → CategoryTicket
    seat_id INT,                              -- FK → Seat
    status VARCHAR(20) NOT NULL,              -- BOOKED, CHECKED_IN, CHECKED_OUT, REFUNDED
    qr_code_value VARCHAR(500),               -- QR code base64
    created_at DATETIME DEFAULT GETDATE(),
    
    FOREIGN KEY (event_id) REFERENCES Event(event_id),
    FOREIGN KEY (user_id) REFERENCES Users(user_id),
    FOREIGN KEY (category_ticket_id) REFERENCES CategoryTicket(category_ticket_id),
    FOREIGN KEY (seat_id) REFERENCES Seat(seat_id)
);

-- Index để tăng tốc query thống kê
CREATE INDEX idx_ticket_event_status ON Ticket(event_id, status);
CREATE INDEX idx_ticket_event_id ON Ticket(event_id);
```

### 📊 Ticket Status Flow

```
BOOKED          →  CHECKED_IN  →  CHECKED_OUT
  ↓
REFUNDED

Status meaning:
- BOOKED: Vé đã đặt, chưa check-in
- CHECKED_IN: Đã vào sự kiện
- CHECKED_OUT: Đã rời sự kiện
- REFUNDED: Đã hoàn tiền (hủy vé)
```

### 🗂️ Mapping File

```
controller/EventStatsController.java    // Controller xử lý stats
    ↓ validates
utils/JwtUtils.java                    // Validate token + extract role
    
controller/EventStatsController.java
    ↓ calls
DAO/TicketDAO.java (getEventStats)     // Query thống kê
    ↓ executes SQL
SQL Server Database (Ticket table)     // Aggregate data
    ↓ returns
DTO/EventStatsResponse.java            // Response structure
```

### 📈 Use Cases

1. **ORGANIZER Dashboard**
   - Xem tổng quan sự kiện của mình
   - Theo dõi tỉ lệ check-in real-time
   - Quyết định có cần thêm staff không

2. **STAFF Dashboard**
   - Giám sát sự kiện được giao
   - Báo cáo cho ORGANIZER

3. **ADMIN Dashboard**
   - Xem thống kê tất cả sự kiện
   - Phân tích hiệu quả tổ chức

### 💡 Nâng cấp đề xuất

```javascript
// Filter theo ngày
GET /api/events/stats?eventId=123&date=2025-01-15

// Thống kê theo category
GET /api/events/stats?eventId=123&categoryId=5

// Real-time updates với WebSocket
ws://localhost:8080/stats?eventId=123

// Export CSV
GET /api/events/stats/export?eventId=123&format=csv
```

---

## 7. TICKET LIST

### 📝 Mô tả
Danh sách vé với phân quyền động: ADMIN xem tất cả, ORGANIZER xem sự kiện của mình, STAFF xem sự kiện được giao.

### 🔗 Luồng xử lý chi tiết

```
1. Frontend
   ↓
   📤 GET /api/tickets/list?eventId=123
   📤 Header: Authorization: Bearer <JWT_TOKEN>
   ↓

2. controller/TicketListController.java (doGet)
   
   ✅ Bước 1: Validate JWT token
      - String authHeader = request.getHeader("Authorization")
      - if (authHeader == null || !authHeader.startsWith("Bearer "))
        → 401 Unauthorized
      - String token = authHeader.substring(7)
   
   ✅ Bước 2: Extract userId và role từ token
      - String role = JwtUtils.getRoleFromToken(token)
      - int userId = JwtUtils.getIdFromToken(token)
      
      Claims structure trong token:
      {
        "userId": 123,
        "email": "a@fpt.edu.vn",
        "role": "ORGANIZER",
        "iat": 1704067200,
        "exp": 1704672000
      }
   
   ✅ Bước 3: Parse eventId (optional)
      - String eventIdStr = request.getParameter("eventId")
      - Integer eventId = (eventIdStr != null) 
          ? Integer.parseInt(eventIdStr) 
          : null
      
      Nếu có eventId: Lọc theo event cụ thể
      Nếu không: Lấy tất cả (tùy role)
   
   ✅ Bước 4: Gọi DAO với phân quyền động
      - TicketDAO.getTicketsByRole(role, userId, eventId)
      
      Logic phân quyền trong DAO:
      
      CASE 1: role = "ADMIN"
         → Xem tất cả tickets
         
         SQL:
         SELECT t.*, e.title, u.full_name, ct.name, s.seat_code
         FROM Ticket t
         LEFT JOIN Event e ON t.event_id = e.event_id
         LEFT JOIN Users u ON t.user_id = u.user_id
         LEFT JOIN CategoryTicket ct ON t.category_ticket_id = ct.category_ticket_id
         LEFT JOIN Seat s ON t.seat_id = s.seat_id
         WHERE (? IS NULL OR t.event_id = ?)  -- Filter eventId nếu có
         ORDER BY t.created_at DESC
      
      CASE 2: role = "ORGANIZER"
         → Xem tickets của events mà ORGANIZER tạo
         
         SQL:
         SELECT t.*, e.title, u.full_name, ct.name, s.seat_code
         FROM Ticket t
         LEFT JOIN Event e ON t.event_id = e.event_id
         LEFT JOIN Users u ON t.user_id = u.user_id
         LEFT JOIN CategoryTicket ct ON t.category_ticket_id = ct.category_ticket_id
         LEFT JOIN Seat s ON t.seat_id = s.seat_id
         WHERE e.organizer_id = ?              -- Chỉ events của ORGANIZER này
           AND (? IS NULL OR t.event_id = ?)   -- Filter eventId nếu có
         ORDER BY t.created_at DESC
      
      CASE 3: role = "STAFF"
         → Xem tickets của events được giao quản lý
         
         SQL:
         SELECT t.*, e.title, u.full_name, ct.name, s.seat_code
         FROM Ticket t
         LEFT JOIN Event e ON t.event_id = e.event_id
         LEFT JOIN StaffEvent se ON e.event_id = se.event_id
         LEFT JOIN Users u ON t.user_id = u.user_id
         LEFT JOIN CategoryTicket ct ON t.category_ticket_id = ct.category_ticket_id
         LEFT JOIN Seat s ON t.seat_id = s.seat_id
         WHERE se.staff_id = ?                 -- Chỉ events staff được giao
           AND (? IS NULL OR t.event_id = ?)   -- Filter eventId nếu có
         ORDER BY t.created_at DESC
      
      CASE 4: role = "STUDENT"
         → Chỉ xem tickets của chính mình
         
         SQL:
         SELECT t.*, e.title, u.full_name, ct.name, s.seat_code
         FROM Ticket t
         LEFT JOIN Event e ON t.event_id = e.event_id
         LEFT JOIN Users u ON t.user_id = u.user_id
         LEFT JOIN CategoryTicket ct ON t.category_ticket_id = ct.category_ticket_id
         LEFT JOIN Seat s ON t.seat_id = s.seat_id
         WHERE t.user_id = ?                   -- Chỉ tickets của user này
           AND (? IS NULL OR t.event_id = ?)   -- Filter eventId nếu có
         ORDER BY t.created_at DESC
   
   ✅ Bước 5: Serialize và trả về
      - List<MyTicketResponse> tickets = dao.getTicketsByRole(...)
      - String json = new Gson().toJson(tickets)
      - response.getWriter().write(json)
      - Status: 200 OK
```

### 🗂️ Database Schema - StaffEvent

```sql
-- Bảng liên kết Staff với Event (Many-to-Many)
CREATE TABLE StaffEvent (
    staff_event_id INT PRIMARY KEY IDENTITY(1,1),
    staff_id INT NOT NULL,           -- FK → Users (role = STAFF)
    event_id INT NOT NULL,           -- FK → Event
    assigned_at DATETIME DEFAULT GETDATE(),
    
    FOREIGN KEY (staff_id) REFERENCES Users(user_id),
    FOREIGN KEY (event_id) REFERENCES Event(event_id),
    
    UNIQUE (staff_id, event_id)      -- 1 staff không được assign 2 lần cho cùng event
);

-- Index
CREATE INDEX idx_staff_event_staff ON StaffEvent(staff_id);
CREATE INDEX idx_staff_event_event ON StaffEvent(event_id);
```

### 📋 Role-based Query Examples

```sql
-- ADMIN: Lấy tất cả tickets
SELECT * FROM Ticket WHERE 1=1;

-- ORGANIZER (userId=5): Lấy tickets của events mình tạo
SELECT t.* 
FROM Ticket t
JOIN Event e ON t.event_id = e.event_id
WHERE e.organizer_id = 5;

-- STAFF (userId=10): Lấy tickets của events được giao
SELECT t.*
FROM Ticket t
JOIN StaffEvent se ON t.event_id = se.event_id
WHERE se.staff_id = 10;

-- STUDENT (userId=20): Chỉ tickets của mình
SELECT * FROM Ticket WHERE user_id = 20;
```

### 🗂️ Mapping File

```
controller/TicketListController.java       // Controller phân quyền
    ↓ extracts
utils/JwtUtils.java                       // Get role + userId from token
    
controller/TicketListController.java
    ↓ calls
DAO/TicketDAO.java (getTicketsByRole)     // Dynamic query based on role
    ↓ queries
SQL Server Database                       // Multiple tables JOIN
    - Ticket
    - Event (organizer_id)
    - StaffEvent (staff assignments)
    - Users, CategoryTicket, Seat
    ↓ returns
DTO/MyTicketResponse.java                 // Response structure
```

---

## 8. STUDENT BILL HISTORY

### 📝 Mô tả
Lịch sử tất cả các hóa đơn thanh toán của sinh viên (VNPAY, wallet...).

### 🔗 Luồng xử lý chi tiết

```
1. Frontend (Student Dashboard)
   ↓
   📤 GET /api/payment/my-bills
   📤 Header: Authorization: Bearer <JWT_TOKEN>
   ↓

2. JWT Authentication (Filter)
   
   filter/JwtAuthFilter.java → doFilter():
   
   ✅ Bước 1: Extract token từ header
      String authHeader = request.getHeader("Authorization");
      String token = authHeader.substring(7);
   
   ✅ Bước 2: Validate và parse token
      if (!JwtUtils.validateToken(token)) {
        → Chuyển sang login page hoặc 401
      }
   
   ✅ Bước 3: Extract userId từ token
      Claims claims = JwtUtils.parseToken(token);
      Integer userId = claims.get("userId", Integer.class);
   
   ✅ Bước 4: Set userId vào request attribute
      request.setAttribute("userId", userId);
   
   ✅ Bước 5: Cho phép request tiếp tục
      chain.doFilter(request, response);
   ↓

3. controller/MyBillsController.java (doGet)
   
   ✅ Bước 1: Lấy userId từ request attribute
      - Object uidObj = request.getAttribute("userId")
      - if (uidObj == null) → 401 Unauthorized
   
   ✅ Bước 2: Parse userId
      - int userId = (Integer) uidObj
      
      Xử lý cả trường hợp:
      - uidObj instanceof Integer → cast trực tiếp
      - uidObj instanceof String → Integer.parseInt()
   
   ✅ Bước 3: Gọi DAO lấy bills
      - BillDAO.getBillsByUserId(userId)
      
      SQL Query:
      
      SELECT 
          b.bill_id,
          b.total_amount,
          b.currency,
          b.payment_method,      -- VNPAY, WALLET, CASH
          b.payment_status,      -- PAID, PENDING, FAILED
          b.created_at,
          u.full_name as user_name
      FROM Bill b
      INNER JOIN Users u ON b.user_id = u.user_id
      WHERE b.user_id = ?
      ORDER BY b.created_at DESC
      
      Join với Users để lấy full_name (hiển thị trên UI)
      
      ResultSet → List<BillResponse>:
      
      while (rs.next()) {
        BillResponse bill = new BillResponse();
        bill.setBillId(rs.getInt("bill_id"));
        bill.setTotalAmount(rs.getBigDecimal("total_amount"));
        bill.setCurrency(rs.getString("currency"));
        bill.setPaymentMethod(rs.getString("payment_method"));
        bill.setPaymentStatus(rs.getString("payment_status"));
        bill.setCreatedAt(rs.getTimestamp("created_at"));
        bill.setUserName(rs.getString("user_name"));
        
        bills.add(bill);
      }
   
   ✅ Bước 4: Convert thành JSON
      - String json = gson.toJson(bills)
   
   ✅ Bước 5: Trả response
      - Status: 200 OK
      - Body: [
          {
            "billId": 456,
            "totalAmount": 500000,
            "currency": "VND",
            "paymentMethod": "VNPAY",
            "paymentStatus": "PAID",
            "createdAt": "2025-01-10T10:30:00",
            "userName": "Nguyễn Văn A"
          },
          ...
        ]
```

### 🗂️ Database Schema

```sql
-- Bảng Bill (hóa đơn)
CREATE TABLE Bill (
    bill_id INT PRIMARY KEY IDENTITY(1,1),
    user_id INT NOT NULL,                     -- FK → Users (người mua)
    total_amount DECIMAL(18, 2) NOT NULL,     -- Tổng tiền
    currency VARCHAR(10) DEFAULT 'VND',       -- Loại tiền tệ
    payment_method VARCHAR(50),               -- VNPAY, WALLET, CASH
    payment_status VARCHAR(20) NOT NULL,      -- PAID, PENDING, FAILED, REFUNDED
    transaction_id VARCHAR(100),              -- ID giao dịch từ gateway
    created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME,
    
    FOREIGN KEY (user_id) REFERENCES Users(user_id)
);

-- Bảng BillItem (chi tiết hóa đơn - các vé trong bill)
CREATE TABLE BillItem (
    bill_item_id INT PRIMARY KEY IDENTITY(1,1),
    bill_id INT NOT NULL,                     -- FK → Bill
    ticket_id INT NOT NULL,                   -- FK → Ticket
    price DECIMAL(18, 2) NOT NULL,            -- Giá vé tại thời điểm mua
    
    FOREIGN KEY (bill_id) REFERENCES Bill(bill_id),
    FOREIGN KEY (ticket_id) REFERENCES Ticket(ticket_id)
);

-- Index
CREATE INDEX idx_bill_user ON Bill(user_id);
CREATE INDEX idx_bill_status ON Bill(payment_status);
CREATE INDEX idx_bill_created ON Bill(created_at DESC);
```

### 💳 Payment Methods

```
1. VNPAY (VNPay Gateway)
   - User chọn thanh toán online
   - Redirect đến VNPay
   - VNPay xử lý và callback
   - Update payment_status = PAID
   
2. WALLET (Ví nội bộ)
   - User có ví trong hệ thống
   - Trừ tiền từ ví
   - Không cần gateway bên ngoài
   
3. CASH (Tiền mặt - offline)
   - Staff nhận tiền tại quầy
   - Staff đánh dấu PAID thủ công
```

### 📊 Payment Status Flow

```
PENDING → PAID
   ↓
FAILED

hoặc

PAID → REFUNDED
```

### 🗂️ Mapping File

```
Frontend
    ↓ sends request
Jakarta Servlet Container
    ↓ intercepts
filter/JwtAuthFilter.java                  // Authentication
    ↓ sets attribute
request.setAttribute("userId", userId)
    ↓ forwards to
controller/MyBillsController.java          // Controller
    ↓ gets attribute
request.getAttribute("userId")
    ↓ calls
DAO/BillDAO.java (getBillsByUserId)        // Database query
    ↓ queries
SQL Server Database
    - Bill (JOIN Users)
    ↓ returns
DTO/BillResponse.java                      // Response structure
```

---

## 9. CRUD VENUE

### 📝 Mô tả
Quản lý địa điểm tổ chức sự kiện (FPT Hòa Lạc, FPT TP.HCM...).

### 🔗 CRUD Operations

```
1. GET /api/venues - LẤY DANH SÁCH VENUES
   
   Authentication: KHÔNG CẦN (Public endpoint)
   
   Flow:
   ✅ Bước 1: Gọi VenueDAO.getAllVenues()
   
   SQL với nested areas (LEFT JOIN):
   
   SELECT 
       v.venue_id,
       v.venue_name,
       v.address,
       v.status,
       va.area_id,
       va.area_name,
       va.capacity,
       va.status as area_status
   FROM Venue v
   LEFT JOIN VenueArea va ON v.venue_id = va.venue_id
   WHERE v.status = 'AVAILABLE'
   ORDER BY v.venue_name, va.area_name
   
   ✅ Bước 2: Group areas by venue
   
   Java code trong DAO:
   
   Map<Integer, Venue> venueMap = new HashMap<>();
   
   while (rs.next()) {
       int venueId = rs.getInt("venue_id");
       
       // Nếu venue chưa có trong map, tạo mới
       if (!venueMap.containsKey(venueId)) {
           Venue v = new Venue();
           v.setVenueId(venueId);
           v.setVenueName(rs.getString("venue_name"));
           v.setAddress(rs.getString("address"));
           v.setStatus(rs.getString("status"));
           v.setAreas(new ArrayList<>());
           
           venueMap.put(venueId, v);
       }
       
       // Thêm area vào venue
       int areaId = rs.getInt("area_id");
       if (!rs.wasNull()) {  // Có area
           VenueArea area = new VenueArea();
           area.setAreaId(areaId);
           area.setAreaName(rs.getString("area_name"));
           area.setCapacity(rs.getInt("capacity"));
           area.setStatus(rs.getString("area_status"));
           
           venueMap.get(venueId).getAreas().add(area);
       }
   }
   
   return new ArrayList<>(venueMap.values());
   
   ✅ Bước 3: Trả response
   
   Response: [
     {
       "venueId": 1,
       "venueName": "FPT University Hòa Lạc",
       "address": "Km29 Đại lộ Thăng Long",
       "status": "AVAILABLE",
       "areas": [
         {
           "areaId": 101,
           "areaName": "Hall A",
           "capacity": 500,
           "status": "AVAILABLE"
         },
         {
           "areaId": 102,
           "areaName": "Hall B",
           "capacity": 300,
           "status": "AVAILABLE"
         }
       ]
     },
     ...
   ]

---

2. POST /api/venues - TẠO VENUE MỚI
   
   Authentication: JWT + ADMIN role required
   
   Request body:
   {
     "venueName": "FPT Đà Nẵng",
     "address": "Khu công nghệ cao Đà Nẵng"
   }
   
   Flow:
   ✅ Bước 1: Validate JWT + role ADMIN
      - JwtUtils.validateToken(token)
      - JwtUtils.getRoleFromToken(token) == "ADMIN"
   
   ✅ Bước 2: Parse request body
      - Gson.fromJson(sb.toString(), Venue.class)
   
   ✅ Bước 3: Validate input
      - VenueService.createVenue(venue)
      
      Validation trong service:
      if (venue.getVenueName() == null || venue.getVenueName().trim().isEmpty()) {
        return Map.of("success", false, "message", "Venue name is required");
      }
      
      if (venue.getVenueName().length() > 200) {
        return Map.of("success", false, "message", "Venue name too long");
      }
   
   ✅ Bước 4: Check duplicate
      - VenueDAO.existsByName(venueName)
      
      SQL:
      SELECT COUNT(*) FROM Venue WHERE venue_name = ?
   
   ✅ Bước 5: Insert vào database
      - VenueDAO.insertVenue(venue)
      
      SQL:
      INSERT INTO Venue (venue_name, address, status, created_at)
      VALUES (?, ?, 'AVAILABLE', GETDATE())
   
   ✅ Bước 6: Trả response
      - Status: 201 Created
      - Body: {
          "status": "success",
          "message": "Venue created successfully"
        }

---

3. PUT /api/venues - CẬP NHẬT VENUE
   
   Authentication: JWT + ADMIN role required
   
   Request body:
   {
     "venueId": 1,
     "venueName": "FPT University Hòa Lạc (Updated)",
     "address": "Km29 Đại lộ Thăng Long, Hà Nội",
     "status": "AVAILABLE"
   }
   
   Flow:
   ✅ Bước 1: Validate JWT + ADMIN
   
   ✅ Bước 2: Parse request body
   
   ✅ Bước 3: Validate venueId required
      if (venueId == null) → 400 Bad Request
   
   ✅ Bước 4: Check venue tồn tại
      - VenueDAO.findById(venueId)
      if (venue == null) → 404 Not Found
   
   ✅ Bước 5: Update database
      - VenueDAO.updateVenue(venue)
      
      SQL:
      UPDATE Venue
      SET venue_name = ?,
          address = ?,
          status = ?,
          updated_at = GETDATE()
      WHERE venue_id = ?
   
   ✅ Bước 6: Trả response
      - Status: 200 OK
      - Body: {
          "status": "success",
          "message": "Venue updated successfully"
        }

---

4. DELETE /api/venues?venueId=1 - XÓA VENUE (SOFT DELETE)
   
   Authentication: JWT + ADMIN role required
   
   Query parameter: venueId=1
   
   Flow:
   ✅ Bước 1: Validate JWT + ADMIN
   
   ✅ Bước 2: Parse venueId từ query parameter
      String venueIdStr = request.getParameter("venueId");
      Integer venueId = Integer.parseInt(venueIdStr);
   
   ✅ Bước 3: Check venue tồn tại
      - VenueDAO.findById(venueId)
   
   ✅ Bước 4: Soft delete (không xóa vật lý)
      - VenueDAO.softDelete(venueId)
      
      SQL:
      UPDATE Venue
      SET status = 'UNAVAILABLE',
          updated_at = GETDATE()
      WHERE venue_id = ?
      
      Lý do soft delete:
      - Giữ lại dữ liệu lịch sử
      - Venue có thể được "restore" sau này
      - Không phá vỡ foreign key constraints
   
   ✅ Bước 5: Trả response
      - Status: 200 OK
      - Body: {
          "status": "success",
          "message": "Venue deleted successfully"
        }
```

### 🗂️ Database Schema

```sql
-- Bảng Venue (địa điểm tổ chức)
CREATE TABLE Venue (
    venue_id INT PRIMARY KEY IDENTITY(1,1),
    venue_name NVARCHAR(200) NOT NULL,        -- Tên địa điểm
    address NVARCHAR(500),                    -- Địa chỉ
    status VARCHAR(20) DEFAULT 'AVAILABLE',   -- AVAILABLE, UNAVAILABLE
    created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME,
    
    UNIQUE (venue_name)                       -- Tên venue không trùng
);

-- Index
CREATE INDEX idx_venue_status ON Venue(status);
CREATE INDEX idx_venue_name ON Venue(venue_name);
```

### 🗂️ Mapping File

```
controller/VenueController.java           // Controller CRUD
    ↓ validates
utils/JwtUtils.java                       // JWT validation + role check
    
controller/VenueController.java
    ↓ calls
service/VenueService.java                 // Business logic + validation
    ↓ calls
DAO/VenueDAO.java                         // Database operations
    ↓ connects
mylib/DBUtils.java                        // Connection pool
    ↓ queries
SQL Server Database (Venue table)         // Data storage

DTO/Venue.java                            // Venue entity
```

---

## 10. VENUE-AREA

### 📝 Mô tả
Quản lý các khu vực trong địa điểm (Hall A, Room 101...) và tự động tạo ghế ngồi.

### 🔗 CRUD Operations với Auto-generate Seats

```
1. GET /api/venues/areas - LẤY DANH SÁCH AREAS
   
   Query parameters (optional):
   - venueId: Lọc theo venue cụ thể
   
   Flow:
   ✅ Nếu có venueId:
      - VenueAreaDAO.getAreasByVenueId(venueId)
      
      SQL:
      SELECT * FROM VenueArea
      WHERE venue_id = ?
      ORDER BY area_name
   
   ✅ Nếu không có venueId:
      - VenueAreaDAO.getAllAreas()
      
      SQL:
      SELECT * FROM VenueArea
      ORDER BY venue_id, area_name

---

2. POST /api/venues/areas - TẠO AREA MỚI + AUTO GENERATE SEATS
   
   Request body:
   {
     "venueId": 1,
     "areaName": "Hall C",
     "capacity": 300
   }
   
   Flow:
   ✅ Bước 1: Validate JWT + ADMIN role
   
   ✅ Bước 2: Parse request body
   
   ✅ Bước 3: Validate input
      - VenueAreaService.createArea(area)
      
      Validations:
      - areaName không rỗng
      - capacity > 0
      - venueId tồn tại (FK constraint)
   
   ✅ Bước 4: Insert area vào database
      - VenueAreaDAO.insertArea(area)
      
      SQL:
      INSERT INTO VenueArea (venue_id, area_name, capacity, status)
      VALUES (?, ?, ?, 'AVAILABLE')
      
      Return: areaId (newly inserted ID)
   
   ✅ Bước 5: AUTO GENERATE SEATS 🎯
      - SeatDAO.generateSeatsForArea(areaId, capacity)
      
      Logic:
      
      // Tạo seat codes: A-01, A-02, ..., A-99, B-01, ...
      int seatsPerRow = 10;  // Mỗi hàng 10 ghế
      int totalRows = (int) Math.ceil(capacity / (double) seatsPerRow);
      
      for (int row = 0; row < totalRows; row++) {
          char rowLetter = (char) ('A' + row);  // A, B, C, ...
          
          for (int seat = 1; seat <= seatsPerRow; seat++) {
              if ((row * seatsPerRow + seat) > capacity) break;
              
              String seatCode = String.format("%c-%02d", rowLetter, seat);
              // Ví dụ: A-01, A-02, ..., B-01, B-02
              
              // Insert seat
              SeatDAO.insertSeat(areaId, seatCode);
          }
      }
      
      SQL (batch insert):
      INSERT INTO Seat (area_id, seat_code, status)
      VALUES (?, ?, 'AVAILABLE')
      
      Ví dụ với capacity=300:
      - 30 rows (A-Z, AA-AD)
      - 10 seats per row
      - Total: 300 seats
      
      Generated seats:
      A-01, A-02, ..., A-10
      B-01, B-02, ..., B-10
      ...
      Z-01, Z-02, ..., Z-10
      AA-01, AA-02, ..., AA-10
      ...
   
   ✅ Bước 6: Trả response
      - Status: 201 Created
      - Body: {
          "status": "success",
          "message": "Area created successfully",
          "areaId": 102
        }

---

3. PUT /api/venues/areas - CẬP NHẬT AREA
   
   Request body:
   {
     "areaId": 102,
     "areaName": "Hall C (Updated)",
     "capacity": 350,
     "status": "AVAILABLE"
   }
   
   Flow:
   ✅ Bước 1-4: Giống POST
   
   ✅ Bước 5: Update database
      - VenueAreaDAO.updateArea(area)
      
      SQL:
      UPDATE VenueArea
      SET area_name = ?,
          capacity = ?,
          status = ?,
          updated_at = GETDATE()
      WHERE area_id = ?
   
   ⚠️ Lưu ý về capacity:
   - Nếu capacity tăng (300 → 350):
     → Có thể auto-generate thêm 50 seats
   
   - Nếu capacity giảm (300 → 250):
     → KHÔNG xóa seats cũ (giữ lại để tránh mất dữ liệu booking)
     → Chỉ đánh dấu excess seats là UNAVAILABLE

---

4. DELETE /api/venues/areas?areaId=102 - XÓA AREA (SOFT DELETE)
   
   Flow:
   ✅ Bước 1: Validate JWT + ADMIN
   
   ✅ Bước 2: Parse areaId
   
   ✅ Bước 3: Check có tickets booked không
      - TicketDAO.countBookedTicketsByAreaId(areaId)
      
      SQL:
      SELECT COUNT(*)
      FROM Ticket t
      JOIN Seat s ON t.seat_id = s.seat_id
      WHERE s.area_id = ?
        AND t.status IN ('BOOKED', 'CHECKED_IN')
      
      Nếu count > 0 → 400 Bad Request
      Message: "Cannot delete area with active bookings"
   
   ✅ Bước 4: Soft delete area
      - VenueAreaDAO.softDelete(areaId)
      
      SQL:
      UPDATE VenueArea
      SET status = 'UNAVAILABLE'
      WHERE area_id = ?
   
   ✅ Bước 5: Soft delete tất cả seats trong area
      - SeatDAO.softDeleteByAreaId(areaId)
      
      SQL:
      UPDATE Seat
      SET status = 'UNAVAILABLE'
      WHERE area_id = ?
```

### 🗂️ Database Schema

```sql
-- Bảng VenueArea (khu vực trong venue)
CREATE TABLE VenueArea (
    area_id INT PRIMARY KEY IDENTITY(1,1),
    venue_id INT NOT NULL,                    -- FK → Venue
    area_name NVARCHAR(100) NOT NULL,         -- Hall A, Room 101
    capacity INT NOT NULL,                    -- Số ghế tối đa
    status VARCHAR(20) DEFAULT 'AVAILABLE',   -- AVAILABLE, UNAVAILABLE
    created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME,
    
    FOREIGN KEY (venue_id) REFERENCES Venue(venue_id),
    UNIQUE (venue_id, area_name)              -- Tên area không trùng trong cùng venue
);

-- Bảng Seat (ghế ngồi)
CREATE TABLE Seat (
    seat_id INT PRIMARY KEY IDENTITY(1,1),
    area_id INT NOT NULL,                     -- FK → VenueArea
    seat_code VARCHAR(10) NOT NULL,           -- A-01, B-12, AA-05
    status VARCHAR(20) DEFAULT 'AVAILABLE',   -- AVAILABLE, BOOKED, UNAVAILABLE
    created_at DATETIME DEFAULT GETDATE(),
    
    FOREIGN KEY (area_id) REFERENCES VenueArea(area_id),
    UNIQUE (area_id, seat_code)               -- Seat code không trùng trong area
);

-- Index
CREATE INDEX idx_area_venue ON VenueArea(venue_id);
CREATE INDEX idx_seat_area ON Seat(area_id);
CREATE INDEX idx_seat_status ON Seat(status);
```

### 🎯 Auto-generate Seats Algorithm

```java
public void generateSeatsForArea(int areaId, int capacity) {
    int SEATS_PER_ROW = 10;
    int totalRows = (int) Math.ceil(capacity / (double) SEATS_PER_ROW);
    
    Connection conn = null;
    PreparedStatement ps = null;
    
    try {
        conn = DBUtils.getConnection();
        String sql = "INSERT INTO Seat (area_id, seat_code, status) VALUES (?, ?, 'AVAILABLE')";
        ps = conn.prepareStatement(sql);
        
        int seatCount = 0;
        
        for (int row = 0; row < totalRows; row++) {
            String rowCode = getRowCode(row);  // A, B, ..., Z, AA, AB, ...
            
            for (int seat = 1; seat <= SEATS_PER_ROW; seat++) {
                if (seatCount >= capacity) break;
                
                String seatCode = String.format("%s-%02d", rowCode, seat);
                
                ps.setInt(1, areaId);
                ps.setString(2, seatCode);
                ps.addBatch();
                
                seatCount++;
                
                // Execute batch mỗi 100 rows
                if (seatCount % 100 == 0) {
                    ps.executeBatch();
                }
            }
        }
        
        ps.executeBatch();  // Execute remaining
        
    } finally {
        // Close resources
    }
}

private String getRowCode(int rowIndex) {
    if (rowIndex < 26) {
        return String.valueOf((char) ('A' + rowIndex));
    } else {
        int firstLetter = rowIndex / 26 - 1;
        int secondLetter = rowIndex % 26;
        return "" + (char) ('A' + firstLetter) + (char) ('A' + secondLetter);
    }
}
```

### 🗂️ Mapping File

```
controller/VenueAreaController.java       // Controller CRUD
    ↓ validates
utils/JwtUtils.java                       // JWT + role check
    
controller/VenueAreaController.java
    ↓ calls
service/VenueAreaService.java             // Business logic
    ↓ calls
DAO/VenueAreaDAO.java                     // Area operations
    ↓ returns areaId
controller/VenueAreaController.java
    ↓ auto-generates seats
DAO/SeatDAO.java (generateSeatsForArea)   // Seat generation
    ↓ inserts
SQL Server Database                       // Venue Area, Seat tables

DTO/VenueArea.java                        // Area entity
```

---

## 11. ADMIN CRUD ACCOUNT

### 📝 Mô tả
ADMIN tạo, sửa, xóa tài khoản STAFF/ORGANIZER (không cho tạo STUDENT).

### 🔗 CRUD Operations

```
1. POST /api/admin/create-account - TẠO TÀI KHOẢN MỚI
   
   Authentication: JWT + ADMIN role required
   
   Request body:
   {
     "role": "STAFF",
     "fullName": "Nguyễn Văn B",
     "email": "b@fpt.edu.vn",
     "phone": "0901234568",
     "password": "Pass123"
   }
   
   Flow:
   ✅ Bước 1: Validate JWT + ADMIN role
      - String authHeader = request.getHeader("Authorization")
      - String token = authHeader.substring(7)
      - String role = JwtUtils.getRoleFromToken(token)
      - if (!"ADMIN".equalsIgnoreCase(role)) → 403 Forbidden
   
   ✅ Bước 2: Parse request body
      - AdminCreateAccountRequest data = gson.fromJson(...)
   
   ✅ Bước 3: Validate role
      - ValidationUtil.isValidRoleForCreation(role)
      
      Logic:
      boolean isValidRoleForCreation(String role) {
        return role != null && 
               (role.equalsIgnoreCase("STAFF") ||
                role.equalsIgnoreCase("ORGANIZER") ||
                role.equalsIgnoreCase("ADMIN"));
      }
      
      Không cho phép tạo STUDENT từ admin panel
      (STUDENT tự đăng ký qua /api/register)
   
   ✅ Bước 4: Validate các field
      - ValidationUtil.isValidFullName(fullName)
      - ValidationUtil.isValidEmail(email)
      - ValidationUtil.isValidVNPhone(phone)
      - ValidationUtil.isValidPassword(password)
   
   ✅ Bước 5: Check email và phone trùng
      - UsersDAO.isEmailExists(email)
        SQL: SELECT COUNT(*) FROM Users WHERE email = ?
      
      - UsersDAO.isPhoneExists(phone)
        SQL: SELECT COUNT(*) FROM Users WHERE phone = ?
      
      - Nếu trùng → 400 Bad Request
   
   ✅ Bước 6: Hash password
      - String hash = PasswordUtils.hashPassword(password)
   
   ✅ Bước 7: Insert vào database
      - UsersDAO.adminCreateAccount(data, hash)
      
      SQL:
      INSERT INTO Users (
        full_name, 
        email, 
        phone, 
        password_hash, 
        role,               -- STAFF, ORGANIZER, ADMIN
        status,             -- ACTIVE (mặc định)
        created_at
      ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', GETDATE())
   
   ✅ Bước 8: Trả response
      - Status: 201 Created
      - Body: {
          "message": "Tạo tài khoản thành công"
        }

---

2. PUT /api/admin/create-account - CẬP NHẬT TÀI KHOẢN
   
   Request body:
   {
     "id": 123,
     "fullName": "Nguyễn Văn B (Updated)",
     "phone": "0901234569",
     "role": "ORGANIZER",
     "status": "ACTIVE",
     "password": "NewPass123"  // Optional: chỉ gửi nếu muốn đổi password
   }
   
   Flow:
   ✅ Bước 1: Validate JWT + ADMIN
   
   ✅ Bước 2: Parse request body
      - AdminUpdateUserRequest data = gson.fromJson(...)
   
   ✅ Bước 3: Validate id required
      - if (data.getId() <= 0) → 400 Bad Request
   
   ✅ Bước 4: Validate các field (nếu có)
      - Chỉ validate field nào được gửi lên (partial update)
      
      if (data.getRole() != null) {
        ValidationUtil.isValidRoleForCreation(role)
      }
      
      if (data.getFullName() != null) {
        ValidationUtil.isValidFullName(fullName)
      }
      
      if (data.getPhone() != null) {
        ValidationUtil.isValidVNPhone(phone)
      }
      
      if (data.getStatus() != null) {
        // Chỉ cho phép ACTIVE hoặc INACTIVE
        if (!("ACTIVE".equals(status) || "INACTIVE".equals(status))) {
          → 400 Bad Request
        }
      }
      
      if (data.getPassword() != null && !data.getPassword().isEmpty()) {
        ValidationUtil.isValidPassword(password)
      }
   
   ✅ Bước 5: Hash password mới (nếu có)
      String passwordHash = null;
      if (data.getPassword() != null && !data.getPassword().trim().isEmpty()) {
        passwordHash = PasswordUtils.hashPassword(data.getPassword());
      }
   
   ✅ Bước 6: Update database
      - UsersDAO.adminUpdateUserById(id, fullName, phone, role, status, passwordHash)
      
      SQL (dynamic update):
      UPDATE Users
      SET 
        full_name = COALESCE(?, full_name),
        phone = COALESCE(?, phone),
        role = COALESCE(?, role),
        status = COALESCE(?, status),
        password_hash = COALESCE(?, password_hash),
        updated_at = GETDATE()
      WHERE user_id = ?
      
      COALESCE: Chỉ update field nào không null
   
   ✅ Bước 7: Trả response
      - Status: 200 OK
      - Body: {
          "message": "Cập nhật tài khoản thành công"
        }

---

3. DELETE /api/admin/create-account?id=123 - XÓA TÀI KHOẢN (SOFT DELETE)
   
   Query parameter: id=123
   
   Flow:
   ✅ Bước 1: Validate JWT + ADMIN
   
   ✅ Bước 2: Parse id từ query parameter
      - String idParam = request.getParameter("id")
      - int userId = Integer.parseInt(idParam)
   
   ✅ Bước 3: Soft delete
      - UsersDAO.softDeleteUser(userId)
      
      SQL:
      UPDATE Users
      SET status = 'INACTIVE',
          updated_at = GETDATE()
      WHERE user_id = ?
      
      Lý do soft delete:
      - Giữ lại dữ liệu lịch sử (tickets, bills...)
      - Có thể restore sau này (set status = ACTIVE)
      - Không phá vỡ foreign key constraints
   
   ✅ Bước 4: Trả response
      - Status: 200 OK
      - Body: {
          "message": "Xóa mềm thành công (status=INACTIVE)"
        }
```

### 🗂️ Database Operations

```sql
-- Insert user mới (Admin create)
INSERT INTO Users (
    full_name, 
    email, 
    phone, 
    password_hash, 
    role,          -- STAFF, ORGANIZER, ADMIN
    status,        -- ACTIVE
    created_at
) VALUES (?, ?, ?, ?, ?, 'ACTIVE', GETDATE());

-- Update user (Admin edit)
UPDATE Users
SET 
    full_name = COALESCE(?, full_name),
    phone = COALESCE(?, phone),
    role = COALESCE(?, role),
    status = COALESCE(?, status),
    password_hash = COALESCE(?, password_hash),
    updated_at = GETDATE()
WHERE user_id = ?;

-- Soft delete (Admin delete)
UPDATE Users
SET 
    status = 'INACTIVE',
    updated_at = GETDATE()
WHERE user_id = ?;

-- Check email exists
SELECT COUNT(*) FROM Users WHERE email = ?;

-- Check phone exists
SELECT COUNT(*) FROM Users WHERE phone = ?;
```

### 🔐 Role Hierarchy

```
ADMIN (highest)
  ↓
  Có thể tạo/sửa/xóa: STAFF, ORGANIZER, ADMIN
  ↓
ORGANIZER
  ↓
  Tạo events, quản lý events của mình
  ↓
STAFF
  ↓
  Quản lý events được assign, check-in/check-out
  ↓
STUDENT (lowest)
  ↓
  Tham gia events, mua vé
```

### 🗂️ Mapping File

```
controller/AdminCreateAccountController.java  // Controller CRUD
    ↓ validates
utils/JwtUtils.java                          // JWT + ADMIN role check
    
controller/AdminCreateAccountController.java
    ↓ validates input
mylib/ValidationUtil.java                    // Validate email, phone, password, role
    
controller/AdminCreateAccountController.java
    ↓ calls
DAO/UsersDAO.java                            // Database operations
    ↓ uses
utils/PasswordUtils.java                     // Hash password
    ↓ inserts/updates
SQL Server Database (Users table)            // Store users

DTO/AdminCreateAccountRequest.java           // Request body (create)
DTO/AdminUpdateUserRequest.java              // Request body (update)
```

### ⚠️ Security Notes

1. **Role Validation**: Chỉ ADMIN mới được tạo/sửa/xóa user
2. **Password Hashing**: Luôn hash trước khi lưu DB
3. **Email/Phone Unique**: Check trùng lặp trước khi insert
4. **Soft Delete**: Không xóa vật lý, set status = INACTIVE
5. **Role Restriction**: Không cho tạo STUDENT từ admin panel

---

## 📚 TỔNG KẾT

### 🎯 Các bảng chính trong Database

```
Users
  ├── Ticket (user_id FK)
  ├── Bill (user_id FK)
  ├── Event (organizer_id FK)
  └── StaffEvent (staff_id FK)

Event
  ├── Ticket (event_id FK)
  ├── StaffEvent (event_id FK)
  └── EventSeatLayout (event_id FK)

Venue
  └── VenueArea (venue_id FK)
      └── Seat (area_id FK)
          └── Ticket (seat_id FK)

Bill
  └── BillItem (bill_id FK)
      └── Ticket (ticket_id FK)

CategoryTicket
  └── Ticket (category_ticket_id FK)
```

### 🔒 Security Layer

```
1. JWT Authentication (filter/JwtAuthFilter.java)
   - Validate token
   - Extract userId, role
   - Set request attributes

2. Role-based Authorization
   - ADMIN: Full access
   - ORGANIZER: Own events
   - STAFF: Assigned events
   - STUDENT: Own tickets/bills

3. Password Security
   - SHA-256 hashing (nên nâng cấp BCrypt)
   - Min 6 chars, letters + digits

4. Input Validation
   - Email: @fpt.edu.vn only
   - Phone: VN format (0901234567)
   - SQL Injection: PreparedStatement

5. CORS Protection
   - Whitelist origins
   - Credentials allowed
```

### 📈 Performance Optimization

```
1. Database Indexes
   - user_id, event_id, status
   - created_at DESC (sorting)

2. Connection Pooling
   - DBUtils.getConnection()
   - Reuse connections

3. Batch Inserts
   - Auto-generate seats (batch 100)

4. Pagination
   - Limit results (avoid memory issues)

5. Caching (đề xuất)
   - Redis cho OTP, sessions
   - Cache event lists
```

---

**🎉 HẾT TÀI LIỆU PHẦN 2**