package org.acme.model;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

@Entity
public class Talk extends PanacheEntityBase implements Comparable<Talk> {

    @Id
    public Long id;

    public String title;

    // At least one description must be filled
    @JdbcTypeCode(Types.LONGVARCHAR)
    @Length(max = 10000)
    public String description;

    public String date;
    public String estTime;
    public String cetTime;
    public String scheduledDuration;
    @URL
    public String liveLink;

    @JoinTable(name = "talk_speaker", joinColumns = @JoinColumn(name = "talk_id"), inverseJoinColumns = @JoinColumn(name = "speakers_id"))
    @ManyToMany
    public List<Speaker> speakers = new ArrayList<Speaker>();

    public int compareTo(Talk other) {
        return this.title.compareTo(other.title);
    }

}