/*
 * Copyright (C) 2014 Desmond Kirrane
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.dkirrane.gitflow.groovy.prompt

import static org.fusesource.jansi.Ansi.Color.GREEN
import static org.fusesource.jansi.Ansi.ansi

/**
 *
 */
@Singleton(lazy=true)
class Prompter {

    String prompt(String message) {
        Scanner scanner = new Scanner(System.in);
        System.out.println(ansi().fg(GREEN).bold().a(message).boldOff().reset().toString());
        String answer = scanner.nextLine();
        return answer;
    }
}

