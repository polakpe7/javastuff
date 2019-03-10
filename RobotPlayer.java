package javastuff;

import battlecode.common.*;

public strictfp class RobotPlayer {

    static RobotController rc;

    /*
        global
     */

    static MapLocation[] enemyArchonLocations;


    static void donate() throws GameActionException {
        float bulletsToWin = rc.getVictoryPointCost() * (GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints());
        if (rc.getTeamBullets() > bulletsToWin
                || rc.getRoundNum() + 1 >= rc.getRoundLimit())
            rc.donate(rc.getTeamBullets());
        else if (rc.getRoundNum() > 100
                && rc.getTeamBullets() > 1000)
            rc.donate(rc.getTeamBullets() - 1000);
    }


    static void tryShakeTree() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(rc.getType().bodyRadius +
                GameConstants.INTERACTION_DIST_FROM_EDGE, Team.NEUTRAL);
        for (TreeInfo tree : trees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.getID())) {
                rc.shake(tree.getID());
                break;
            }
        }
    }

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        enemyArchonLocations = rc.getInitialArchonLocations(Team.B);
        archons = enemyArchonLocations.length;
        myTeam = rc.getTeam();
        enemyTeam = myTeam.opponent();

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
        }
    }

    /*
     ********************************************************************************************
     *                                        ARCHON
     ********************************************************************************************
     */

    static int archons = 0;
    static Team myTeam;
    static Team enemyTeam;

    static final int ARCHON_LEADER_ECHO = 5;
    static final int ARCHON_LEADER_CHANNEL = 1;
    static final int ARCHON_LEADER_ECHO_CHANNEL = 2;

    static int round;

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");

        int myID = rc.getID();
        int leaderID;
        int lastLeaderEcho;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                leaderID = rc.readBroadcastInt(ARCHON_LEADER_CHANNEL);
                lastLeaderEcho = rc.readBroadcastInt(ARCHON_LEADER_ECHO_CHANNEL);
                round = rc.getRoundNum();

                if (round >= lastLeaderEcho + ARCHON_LEADER_ECHO) {
                    if (leaderID == myID) {
                        rc.broadcastInt(ARCHON_LEADER_ECHO_CHANNEL, round);
                    } else {
                        archons--;
                        rc.broadcastInt(ARCHON_LEADER_ECHO_CHANNEL, round);
                        rc.broadcastInt(ARCHON_LEADER_CHANNEL, myID);
                    }
                }

                donate();


                if (leaderID == myID)
                    archonBuildGardener();


                tryMove(randomDirection());

                tryShakeTree();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    static final int GARDENER_WORKING_CHANNEL = 10;

    static void archonBuildGardener() throws GameActionException {
        int gw = rc.readBroadcastInt(GARDENER_WORKING_CHANNEL);

        if (gw + 200 < round) {
            Direction d = Direction.NORTH;
            for (int i = 0; i < 36; i++) {
                if (i % 2 == 0) {
                    if (rc.canHireGardener(d.rotateLeftDegrees((i >> 2) * 10))) {
                        rc.hireGardener(d.rotateLeftDegrees((i >> 2) * 10));
                        break;
                    }
                } else if (rc.canHireGardener(d.rotateRightDegrees((i >> 2) * 10))) {
                    rc.hireGardener(d.rotateRightDegrees((i >> 2) * 10));
                    break;
                }
            }
        }
    }

    /*
     ********************************************************************************************
     *                                        GARDENER
     ********************************************************************************************
     */

    static final int GARDENER_EARLY_SOLDIER_LIMIT = 300;

    static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");

        int round = rc.getRoundNum();

        rc.broadcastInt(GARDENER_WORKING_CHANNEL, round);

        while (true) {

            try {

                round = rc.getRoundNum();

                donate();

                if (round < GARDENER_EARLY_SOLDIER_LIMIT) {
                    Direction d = Direction.NORTH;
                    for (int i = 0; i < 36; i++) {
                        if (i % 2 == 0) {
                            if (rc.canBuildRobot(RobotType.SOLDIER, d.rotateLeftDegrees((i >> 2) * 10))) {
                                rc.buildRobot(RobotType.SOLDIER, d.rotateLeftDegrees((i >> 2) * 10));
                                break;
                            }
                        } else if (rc.canBuildRobot(RobotType.SOLDIER, d.rotateRightDegrees((i >> 2) * 10))) {
                            rc.buildRobot(RobotType.SOLDIER, d.rotateRightDegrees(((i >> 2) * 10)));
                            break;
                        }
                    }
                }

                // Move randomly
                tryMove(randomDirection());

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // If there are some...
                if (robots.length > 0) {
                    // And we have enough bullets, and haven't attacked yet this turn...
                    if (rc.canFireSingleShot()) {
                        // ...Then fire a bullet in the direction of the enemy.
                        rc.fireSingleShot(rc.getLocation().directionTo(robots[0].location));
                    }
                }

                // Move randomly
                tryMove(randomDirection());

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if (robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1, enemy);

                    // If there is a robot, move towards it
                    if (robots.length > 0) {
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = robots[0].getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        tryMove(toEnemy);
                    } else {
                        // Move Randomly
                        tryMove(randomDirection());
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns a random Direction
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float) Math.random() * 2 * (float) Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, 20, 3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir           The intended direction of movement
     * @param degreeOffset  Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            // Try the offset of the left side
            if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck));
                return true;
            }
            // Try the offset on the right side
            if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}