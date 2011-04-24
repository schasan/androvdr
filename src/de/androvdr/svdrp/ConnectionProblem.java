package de.androvdr.svdrp;

import org.hampelratte.svdrp.Response;

public class ConnectionProblem extends Response {

    private static final long serialVersionUID = 1L;

    public ConnectionProblem() {
        super(0, "Couldn't connect to VDR");
    }

    @Override
    public String toString() {
        return getMessage();
    }

}
