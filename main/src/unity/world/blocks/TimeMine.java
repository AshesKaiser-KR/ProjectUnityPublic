package unity.world.blocks;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.world.Block;

import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

public class TimeMine extends Block {
    public float range = 2 * tilesize;
    public float reload = 30f, teleportRange = 500f;
    public Effect tpEffect;

    public TimeMine(String name) {
        super(name);

        update = sync = configurable = true;
        logicConfigurable = solid = rotate = noUpdateDisabled = false;
        size = 1;
        hasPower = hasItems = hasLiquids = false;

        /* link or unlink, link's pos() given as value */
        config(Integer.class, (TimeMineBuild entity, Integer value) -> {
            Building other = world.build(value);
            TimeMineBuild otherB = (TimeMineBuild) other;
            if (entity.teleporter == value && entity.dest == other){
                entity.teleporter = -1;
                entity.dest = entity;
                otherB.from = other;
                otherB.fromPos = other.pos();
            }else if(entity.tpValid(entity, other) && entity.teleporter != other.pos()){
                entity.teleporter = other.pos();
                entity.dest = other;
                otherB.from = entity;
                otherB.fromPos = entity.pos();
            }

            if (entity.dest == other && otherB.dest == entity){
                otherB.teleporter = -1;
                otherB.dest = other;
                entity.from = entity;
                entity.fromPos = entity.pos();
                entity.teleporter = other.pos();
                entity.dest = other;
                otherB.from = entity;
                otherB.fromPos = entity.pos();
            }
        });
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * tilesize, y * tilesize, range, Pal.accent);
        Drawf.dashCircle(x * tilesize, y * tilesize, teleportRange, Pal.accent);

        Draw.reset();
    }

    public class TimeMineBuild extends Building{
        public Building dest = this, from = this;
        public Seq<Unit> teleportUnit = new Seq<>();
        /* stores linked teleporter's position in pos() */
        public int teleporter, fromPos = -1;

        @Override
        public void updateTile(){
            if (teleporter != -1 && dest == this) {
                dest = world.build(teleporter);
            }

            if(connected()){
                Units.nearbyEnemies(team, x, y, range, e -> {
                    e.impulseNet(Tmp.v1.trns(e.angleTo(this), e.dst(this) - e.vel.len()).scl(Time.delta * Mathf.floor(Mathf.pow(e.mass(), 0.5f))));
                    e.disarmed = true;
                    if (e.dst(this) <= 4f){
                        e.set(this);
                        e.vel.limit(0.01f);
                        if (!teleportUnit.contains(e) && teleportUnit.size < 5) teleportUnit.add(e);
                    }
                });

                if (teleportUnit.size > 0) {
                    if (timer(0, reload)) {
                        for (Unit toTeleport: teleportUnit){
                            teleport(toTeleport);
                            teleportUnit.remove(toTeleport);
                        }
                        if (tpEffect != null) tpEffect.at(x, y);
                        damage(health / 10);
                        timer.reset(0, 0);
                    }
                }else{
                    timer.reset(0,0);
                }
            }
        }

        /* adding links */
        @Override
        public boolean onConfigureTileTapped(Building other){
            if (tpValid(this, other)){
                configure(other.pos());
                return false;
            }
            return true;
        }

        @Override
        public void drawConfigure(){
            if (dest != null && teleporter != -1){
                Drawf.circles(dest.x, dest.y, 16f, Pal.accent);
                Drawf.arrow(x, y, dest.x, dest.y, 12f, 6f);
            }

            Drawf.dashCircle(x, y, teleportRange, Pal.accent);
        }

        /* whether this mine is connected to a teleporter. */
        public boolean connected(){
            return dest != null && teleporter != -1 && dest.pos() == teleporter;
        }

        public boolean tpValid(Building tile, Building link){ return tile != link && tile.dst(link) <= teleportRange && link != null && tile.team == link.team && !link.dead() && link instanceof TimeMine.TimeMineBuild; }

        public void teleport(Unit unit){
            unit.set(dest.x, dest.y);
            if (unit.isPlayer() && unit.getPlayer() == Vars.player && !Vars.headless) Core.camera.position.set(dest.x, dest.y);
        }

        @Override
        public void write(Writes write){
            super.write(write);

            write.i(teleporter);
            write.i(fromPos);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);

            teleporter = read.i();
            fromPos = read.i();
        }
    }
}