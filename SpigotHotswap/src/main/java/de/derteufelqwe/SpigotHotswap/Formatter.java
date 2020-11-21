package de.derteufelqwe.SpigotHotswap;

import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class Formatter extends SimpleFormatter {



    @Override
    public synchronized String format(LogRecord record) {

        return "[SHP] " + record.getMessage();
    }



}
