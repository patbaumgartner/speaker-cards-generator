package org.acme.rest;

import java.util.List;

import org.acme.model.Speaker;

import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Path;

public class Website extends Controller {

    @CheckedTemplate
    static class Templates {
        public static native TemplateInstance index(List<Speaker> speakers);
    }

    @Path("/")
    public TemplateInstance index() {
        // list every todo
        List<Speaker> speakers = Speaker.listAll();
        // render the index template
        return Templates.index(speakers);
    }

}
