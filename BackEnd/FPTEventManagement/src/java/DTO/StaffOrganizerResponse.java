package DTO;

import java.util.List;

public class StaffOrganizerResponse {
    private List<Users> staffList;
    private List<Users> organizerList;

    public StaffOrganizerResponse() {}

    public StaffOrganizerResponse(List<Users> staffList, List<Users> organizerList) {
        this.staffList = staffList;
        this.organizerList = organizerList;
    }

    public List<Users> getStaffList() {
        return staffList;
    }

    public void setStaffList(List<Users> staffList) {
        this.staffList = staffList;
    }

    public List<Users> getOrganizerList() {
        return organizerList;
    }

    public void setOrganizerList(List<Users> organizerList) {
        this.organizerList = organizerList;
    }
}
