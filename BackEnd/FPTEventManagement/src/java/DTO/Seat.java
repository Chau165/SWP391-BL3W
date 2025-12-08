package DTO;

public class Seat {

    private int seatId;
    private int areaId;          // area vật lý
    private String seatCode;     // A1, A2
    private String rowNo;
    private String colNo;

    /**
     * status:
     *  - Khi load từ bảng Seat (ghế vật lý): ACTIVE / INACTIVE
     *  - Khi load từ Event_Seat_Layout (layout theo event): AVAILABLE / BOOKED
     */
    private String status;

    /**
     * seatType:
     *  - Khi load từ SeatDAO (ghế vật lý): thường sẽ = null (vì bảng Seat không còn cột này)
     *  - Khi load từ EventSeatLayoutDAO: VIP / STANDARD (theo từng event)
     */
    private String seatType;

    public int getSeatId() {
        return seatId;
    }

    public void setSeatId(int seatId) {
        this.seatId = seatId;
    }

    public int getAreaId() {
        return areaId;
    }

    public void setAreaId(int areaId) {
        this.areaId = areaId;
    }

    public String getSeatCode() {
        return seatCode;
    }

    public void setSeatCode(String seatCode) {
        this.seatCode = seatCode;
    }

    public String getRowNo() {
        return rowNo;
    }

    public void setRowNo(String rowNo) {
        this.rowNo = rowNo;
    }

    public String getColNo() {
        return colNo;
    }

    public void setColNo(String colNo) {
        this.colNo = colNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSeatType() {
        return seatType;
    }

    public void setSeatType(String seatType) {
        this.seatType = seatType;
    }

    @Override
    public String toString() {
        return "Seat{" +
                "seatId=" + seatId +
                ", areaId=" + areaId +
                ", seatCode='" + seatCode + '\'' +
                ", rowNo='" + rowNo + '\'' +
                ", colNo='" + colNo + '\'' +
                ", status='" + status + '\'' +
                ", seatType='" + seatType + '\'' +
                '}';
    }
}
