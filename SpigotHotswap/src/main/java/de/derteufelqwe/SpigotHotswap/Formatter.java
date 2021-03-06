package de.derteufelqwe.SpigotHotswap;

import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Jokingly simple formatter for SpigotHotswaps logs
 */
public class Formatter extends SimpleFormatter {

    @Override
    public synchronized String format(LogRecord record) {
        System.out.println("format: " + record.getMessage());
        return "[SHP] " + record.getMessage();
    }

}
