package model;

/** Represents a recruiting company that visits the campus for placements. */
public class Company {

    private int companyId;
    private String name;
    private String sector;
    private String contactEmail;
    private String contactPhone;

    public Company() {
    }

    public Company(String name, String sector, String contactEmail, String contactPhone) {
        this.name = name;
        this.sector = sector;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
    }

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s | Sector: %s | Contact: %s / %s",
                companyId, name, sector, contactEmail, contactPhone);
    }
}
