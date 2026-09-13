"""
Electional Astrology High-Precision Time-Series Search Engine
============================================================

A senior-grade Python module utilizing the Swiss Ephemeris (`swisseph` / `pysweph`)
to perform continuous planetary calculations, discrete time-series sampling, root-finding
boundary refinement (bisection and linear interpolation), and multi-rule window merging
for classical, medieval, and modern electional astrology.

Author: Senior Astrological Software Engineer
License: MIT
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from enum import Enum, IntEnum
from typing import Any, Callable, Dict, List, Optional, Sequence, Set, Tuple, Union

# ---------------------------------------------------------------------------
# Swiss Ephemeris Dynamic Import & Configuration
# ---------------------------------------------------------------------------
try:
    import swisseph as swe
except ImportError:
    try:
        import swe
    except ImportError:
        raise ImportError(
            "Swiss Ephemeris library not found. Please install either:\n"
            "  pip install py-swisseph\n"
            "or\n"
            "  pip install pysweph"
        )


# ---------------------------------------------------------------------------
# Astronomical & Astrological Constants and Enums
# ---------------------------------------------------------------------------

class CelestialBody(IntEnum):
    SUN = swe.SUN
    MOON = swe.MOON
    MERCURY = swe.MERCURY
    VENUS = swe.VENUS
    MARS = swe.MARS
    JUPITER = swe.JUPITER
    SATURN = swe.SATURN
    URANUS = swe.URANUS
    NEPTUNE = swe.NEPTUNE
    PLUTO = swe.PLUTO
    MEAN_NODE = swe.MEAN_NODE
    TRUE_NODE = swe.TRUE_NODE
    CHIRON = swe.CHIRON

    @property
    def display_name(self) -> str:
        names = {
            CelestialBody.SUN: "Sun",
            CelestialBody.MOON: "Moon",
            CelestialBody.MERCURY: "Mercury",
            CelestialBody.VENUS: "Venus",
            CelestialBody.MARS: "Mars",
            CelestialBody.JUPITER: "Jupiter",
            CelestialBody.SATURN: "Saturn",
            CelestialBody.URANUS: "Uranus",
            CelestialBody.NEPTUNE: "Neptune",
            CelestialBody.PLUTO: "Pluto",
            CelestialBody.MEAN_NODE: "North Node (Mean)",
            CelestialBody.TRUE_NODE: "North Node (True)",
            CelestialBody.CHIRON: "Chiron",
        }
        return names.get(self, self.name)


class ZodiacSign(IntEnum):
    ARIES = 1
    TAURUS = 2
    GEMINI = 3
    CANCER = 4
    LEO = 5
    VIRGO = 6
    LIBRA = 7
    SCORPIO = 8
    SAGITTARIUS = 9
    CAPRICORN = 10
    AQUARIUS = 11
    PISCES = 12

    @property
    def sign_name(self) -> str:
        names = [
            "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
            "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
        ]
        return names[self.value - 1]

    @property
    def element(self) -> str:
        elements = ["Fire", "Earth", "Air", "Water"]
        return elements[(self.value - 1) % 4]

    @property
    def modality(self) -> str:
        modalities = ["Cardinal", "Fixed", "Mutable"]
        return modalities[(self.value - 1) % 3]

    @property
    def traditional_ruler(self) -> CelestialBody:
        rulers = {
            ZodiacSign.ARIES: CelestialBody.MARS,
            ZodiacSign.TAURUS: CelestialBody.VENUS,
            ZodiacSign.GEMINI: CelestialBody.MERCURY,
            ZodiacSign.CANCER: CelestialBody.MOON,
            ZodiacSign.LEO: CelestialBody.SUN,
            ZodiacSign.VIRGO: CelestialBody.MERCURY,
            ZodiacSign.LIBRA: CelestialBody.VENUS,
            ZodiacSign.SCORPIO: CelestialBody.MARS,
            ZodiacSign.SAGITTARIUS: CelestialBody.JUPITER,
            ZodiacSign.CAPRICORN: CelestialBody.SATURN,
            ZodiacSign.AQUARIUS: CelestialBody.SATURN,
            ZodiacSign.PISCES: CelestialBody.JUPITER,
        }
        return rulers[self]


class HouseSystem(str, Enum):
    PLACIDUS = "P"
    KOCH = "K"
    WHOLE_SIGN = "W"
    EQUAL = "A"
    REGIOMONTANUS = "R"
    CAMPANUS = "C"
    PORPHYRY = "O"


class MajorAspect(float, Enum):
    CONJUNCTION = 0.0
    SEXTILE = 60.0
    SQUARE = 90.0
    TRINE = 120.0
    OPPOSITION = 180.0


# ---------------------------------------------------------------------------
# Data Structures
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class GeographicCoordinates:
    latitude: float
    longitude: float
    altitude: float = 0.0

    def __post_init__(self) -> None:
        if not (-90.0 <= self.latitude <= 90.0):
            raise ValueError(f"Latitude must be within [-90, 90], got {self.latitude}")
        if not (-180.0 <= self.longitude <= 180.0):
            raise ValueError(f"Longitude must be within [-180, 180], got {self.longitude}")


@dataclass
class PlanetState:
    body: CelestialBody
    name: str
    longitude: float          # 0.0 to 360.0 degrees
    latitude: float           # Ecliptic latitude
    distance: float          # In AU
    speed_longitude: float   # Deg/day (negative = retrograde)
    speed_latitude: float
    speed_distance: float
    is_retrograde: bool
    sign: ZodiacSign
    sign_degree: float       # 0.0 to 30.0
    house: int               # 1 to 12

    def formatted_position(self) -> str:
        deg = int(self.sign_degree)
        minutes_float = (self.sign_degree - deg) * 60.0
        minute = int(minutes_float)
        second = int((minutes_float - minute) * 60.0)
        rx = " [Rx]" if self.is_retrograde else ""
        return f"{self.name}: {deg:02d}° {self.sign.sign_name} {minute:02d}'{second:02d}\"{rx} in House {self.house}"


@dataclass
class ChartState:
    utc_datetime: datetime
    julian_day_ut: float
    coordinates: GeographicCoordinates
    house_system: HouseSystem
    planets: Dict[CelestialBody, PlanetState]
    house_cusps: List[float]  # 1-indexed list of length 13 (index 0 is dummy)
    ascendant: float          # Degrees (0 to 360)
    midheaven: float          # Degrees (0 to 360)
    armc: float
    vertex: float

    @property
    def ascendant_sign(self) -> ZodiacSign:
        return ZodiacSign(int(self.ascendant // 30) + 1)

    @property
    def ascendant_degree(self) -> float:
        return self.ascendant % 30.0

    @property
    def midheaven_sign(self) -> ZodiacSign:
        return ZodiacSign(int(self.midheaven // 30) + 1)

    @property
    def midheaven_degree(self) -> float:
        return self.midheaven % 30.0

    def get_aspect_separation(self, body1: CelestialBody, body2: CelestialBody) -> float:
        """Returns the shortest arc distance between two bodies (0.0 to 180.0 deg)."""
        lon1 = self.planets[body1].longitude
        lon2 = self.planets[body2].longitude
        diff = abs(lon1 - lon2) % 360.0
        return 360.0 - diff if diff > 180.0 else diff

    def is_aspect_applying(
        self,
        body1: CelestialBody,
        body2: CelestialBody,
        target_aspect_deg: float
    ) -> bool:
        """Determines if the aspect between two planets is applying (approaching exact)."""
        p1 = self.planets[body1]
        p2 = self.planets[body2]
        current_sep = self.get_aspect_separation(body1, body2)
        rel_speed = p1.speed_longitude - p2.speed_longitude

        # Compute separation after a small delta time (1 hour)
        delta_deg1 = (p1.longitude + p1.speed_longitude / 24.0) % 360.0
        delta_deg2 = (p2.longitude + p2.speed_longitude / 24.0) % 360.0
        diff_future = abs(delta_deg1 - delta_deg2) % 360.0
        future_sep = 360.0 - diff_future if diff_future > 180.0 else diff_future

        curr_error = abs(current_sep - target_aspect_deg)
        future_error = abs(future_sep - target_aspect_deg)
        return future_error < curr_error

    def to_dict(self) -> Dict[str, Any]:
        return {
            "utc_datetime": self.utc_datetime.isoformat(),
            "julian_day_ut": self.julian_day_ut,
            "ascendant": {
                "degree": self.ascendant,
                "sign": self.ascendant_sign.sign_name,
                "sign_degree": self.ascendant_degree,
            },
            "midheaven": {
                "degree": self.midheaven,
                "sign": self.midheaven_sign.sign_name,
                "sign_degree": self.midheaven_degree,
            },
            "planets": {
                p.body.name: {
                    "longitude": p.longitude,
                    "sign": p.sign.sign_name,
                    "sign_degree": p.sign_degree,
                    "house": p.house,
                    "speed": p.speed_longitude,
                    "is_retrograde": p.is_retrograde,
                }
                for p in self.planets.values()
            },
            "house_cusps": self.house_cusps[1:],
        }


@dataclass
class ElectionRule:
    """
    Encapsulates an electional astrology condition.
    
    predicate: Callable returning True if the chart satisfies the rule.
    continuous_metric: Optional Callable returning a continuous float f(t) such that
                       f(t) >= 0 indicates condition satisfaction and f(t) == 0 represents
                       the exact boundary root. When provided, allows secant / linear
                       interpolation root finding.
    """
    name: str
    predicate: Callable[[ChartState], bool]
    continuous_metric: Optional[Callable[[ChartState], float]] = None
    description: str = ""

    def evaluate(self, chart: ChartState) -> bool:
        return self.predicate(chart)


@dataclass
class TimeInterval:
    """Continuous UTC time interval [start, end]."""
    start: datetime
    end: datetime

    def __post_init__(self) -> None:
        if self.start > self.end:
            raise ValueError(f"Interval start {self.start} cannot be after end {self.end}")

    @property
    def duration(self) -> timedelta:
        return self.end - self.start

    @property
    def duration_minutes(self) -> float:
        return self.duration.total_seconds() / 60.0

    def overlaps(self, other: TimeInterval) -> bool:
        return max(self.start, other.start) <= min(self.end, other.end)

    def intersect(self, other: TimeInterval) -> Optional[TimeInterval]:
        latest_start = max(self.start, other.start)
        earliest_end = min(self.end, other.end)
        if latest_start <= earliest_end:
            return TimeInterval(latest_start, earliest_end)
        return None


@dataclass
class ElectionWindow:
    """Represents a validated electional time frame with calculated planetary states."""
    interval: TimeInterval
    start_chart: ChartState
    mid_chart: ChartState
    end_chart: ChartState
    satisfied_rules: List[str] = field(default_factory=list)
    quality_score: float = 1.0

    @property
    def start_time(self) -> datetime:
        return self.interval.start

    @property
    def end_time(self) -> datetime:
        return self.interval.end

    @property
    def duration_minutes(self) -> float:
        return self.interval.duration_minutes

    def summary(self) -> str:
        s_dt = self.start_time.strftime("%Y-%m-%d %H:%M:%S UTC")
        e_dt = self.end_time.strftime("%Y-%m-%d %H:%M:%S UTC")
        asc_info = f"{self.mid_chart.ascendant_sign.sign_name} {self.mid_chart.ascendant_degree:.1f}°"
        moon = self.mid_chart.planets.get(CelestialBody.MOON)
        moon_info = f"{moon.sign.sign_name} {moon.sign_degree:.1f}° (H{moon.house})" if moon else "N/A"
        
        return (
            f"Election Window:\n"
            f"  From:     {s_dt}\n"
            f"  To:       {e_dt}\n"
            f"  Duration: {self.duration_minutes:.1f} minutes\n"
            f"  Ascendant (Mid): {asc_info}\n"
            f"  Moon (Mid):      {moon_info}\n"
            f"  Rules satisfied: {', '.join(self.satisfied_rules)}"
        )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "start_time": self.start_time.isoformat(),
            "end_time": self.end_time.isoformat(),
            "duration_minutes": self.duration_minutes,
            "satisfied_rules": self.satisfied_rules,
            "quality_score": self.quality_score,
            "start_chart": self.start_chart.to_dict(),
            "mid_chart": self.mid_chart.to_dict(),
            "end_chart": self.end_chart.to_dict(),
        }


# ---------------------------------------------------------------------------
# Ephemeris Calculation Engine
# ---------------------------------------------------------------------------

class EphemerisEngine:
    """Encapsulates Swiss Ephemeris interactions and continuous chart calculation."""

    def __init__(self, ephe_path: Optional[str] = None):
        if ephe_path:
            swe.set_ephe_path(ephe_path)
        # Determine calc flag: preference for Swiss Ephemeris, fallback to Moshier
        self._calc_flag = swe.FLG_SWIEPH | swe.FLG_SPEED

    @staticmethod
    def datetime_to_julian_day(dt: datetime) -> float:
        """Converts UTC datetime to Julian Day UT."""
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        else:
            dt = dt.astimezone(timezone.utc)

        hour_decimal = (
            dt.hour
            + dt.minute / 60.0
            + dt.second / 3600.0
            + dt.microsecond / 3.6e9
        )
        return float(swe.julday(dt.year, dt.month, dt.day, hour_decimal))

    @staticmethod
    def julian_day_to_datetime(jd_ut: float) -> datetime:
        """Converts Julian Day UT back to UTC datetime."""
        year, month, day, hour_decimal = swe.revjul(jd_ut)
        total_seconds = hour_decimal * 3600.0
        hour = int(total_seconds // 3600)
        remaining = total_seconds % 3600
        minute = int(remaining // 60)
        second_float = remaining % 60
        second = int(second_float)
        microsecond = int(round((second_float - second) * 1e6))
        if microsecond >= 1_000_000:
            microsecond = 999_999

        return datetime(year, month, day, hour, minute, second, microsecond, tzinfo=timezone.utc)

    @staticmethod
    def _determine_house(longitude: float, cusps: Sequence[float]) -> int:
        """
        Determines the house (1-12) for a given longitude based on 1-indexed cusps.
        Handles cusp wrapping past 0°/360° Aries.
        """
        lon = longitude % 360.0
        for i in range(1, 13):
            c_current = cusps[i] % 360.0
            c_next = cusps[1] % 360.0 if i == 12 else cusps[i + 1] % 360.0

            if c_current < c_next:
                if c_current <= lon < c_next:
                    return i
            else:
                # Wraps around 360°
                if lon >= c_current or lon < c_next:
                    return i
        return 1

    def calculate_chart(
        self,
        utc_dt: datetime,
        coords: GeographicCoordinates,
        house_system: HouseSystem = HouseSystem.PLACIDUS,
        bodies: Optional[Sequence[CelestialBody]] = None,
    ) -> ChartState:
        """Calculates exact planetary positions, speed, and house cusps at given UTC datetime."""
        if bodies is None:
            bodies = list(CelestialBody)

        jd_ut = self.datetime_to_julian_day(utc_dt)

        # Calculate Houses and angles (ASC, MC, ARMC, Vertex)
        # swe.houses returns (cusps_tuple, ascmc_tuple)
        h_sys_code = house_system.value.encode("ascii")
        try:
            raw_cusps, ascmc = swe.houses(jd_ut, coords.latitude, coords.longitude, h_sys_code)
        except Exception:
            # Fallback to whole sign or equal if polar house breakdown occurs in Placidus
            raw_cusps, ascmc = swe.houses(jd_ut, coords.latitude, coords.longitude, b"W")

        # In Swiss Ephemeris python bindings, cusps is either 13 elements (index 1..12 used)
        # or 12 elements (index 0..11). We standardize to a 13-element 1-indexed list:
        if len(raw_cusps) == 13:
            cusps = list(raw_cusps)
        else:
            cusps = [0.0] + list(raw_cusps)

        ascendant = ascmc[0] % 360.0
        midheaven = ascmc[1] % 360.0
        armc = ascmc[2]
        vertex = ascmc[3] % 360.0

        planets_state: Dict[CelestialBody, PlanetState] = {}

        for body in bodies:
            try:
                calc_res, _ = swe.calc_ut(jd_ut, body.value, self._calc_flag)
            except Exception:
                # Try with Moshier ephemeris if Swiss Ephemeris data files are missing
                calc_res, _ = swe.calc_ut(jd_ut, body.value, swe.FLG_MOSEPH | swe.FLG_SPEED)

            lon = calc_res[0] % 360.0
            lat = calc_res[1]
            dist = calc_res[2]
            speed_lon = calc_res[3]
            speed_lat = calc_res[4]
            speed_dist = calc_res[5]

            sign_val = int(lon // 30) + 1
            sign = ZodiacSign(sign_val)
            sign_deg = lon % 30.0
            is_rx = speed_lon < 0.0
            house = self._determine_house(lon, cusps)

            planets_state[body] = PlanetState(
                body=body,
                name=body.display_name,
                longitude=lon,
                latitude=lat,
                distance=dist,
                speed_longitude=speed_lon,
                speed_latitude=speed_lat,
                speed_distance=speed_dist,
                is_retrograde=is_rx,
                sign=sign,
                sign_degree=sign_deg,
                house=house,
            )

        return ChartState(
            utc_datetime=utc_dt,
            julian_day_ut=jd_ut,
            coordinates=coords,
            house_system=house_system,
            planets=planets_state,
            house_cusps=cusps,
            ascendant=ascendant,
            midheaven=midheaven,
            armc=armc,
            vertex=vertex,
        )


# ---------------------------------------------------------------------------
# Root Finding Algorithms
# ---------------------------------------------------------------------------

class RootFinder:
    """
    High-precision root-finding and boundary detection algorithms.
    Refines state transition brackets [t_inactive, t_active] down to exact minute/second.
    """

    @staticmethod
    def bisection_boundary(
        predicate: Callable[[datetime], bool],
        t_left: datetime,
        t_right: datetime,
        tolerance_seconds: float = 30.0,
        max_iterations: int = 30,
    ) -> datetime:
        """
        Bisection algorithm for arbitrary discrete boolean predicates.
        Pinpoints the exact inflection boundary where predicate changes value.
        """
        val_left = predicate(t_left)
        val_right = predicate(t_right)

        if val_left == val_right:
            return t_right

        low = t_left
        high = t_right

        for _ in range(max_iterations):
            delta = (high - low).total_seconds()
            if delta <= tolerance_seconds:
                break

            mid = low + timedelta(seconds=delta / 2.0)
            val_mid = predicate(mid)

            if val_mid == val_left:
                low = mid
            else:
                high = mid

        # Return the boundary point on the active side
        return high if val_right else low

    @staticmethod
    def secant_continuous_root(
        metric_func: Callable[[datetime], float],
        t0: datetime,
        t1: datetime,
        target_val: float = 0.0,
        tolerance_seconds: float = 5.0,
        tolerance_metric: float = 1e-4,
        max_iterations: int = 20,
    ) -> datetime:
        """
        Secant / Regula-Falsi hybrid algorithm for continuous metric zero-crossings
        (e.g., exact aspect ingress/egress, cusp longitude transit).
        Provides quadratic-like convergence speed for continuous functions.
        """
        f0 = metric_func(t0) - target_val
        f1 = metric_func(t1) - target_val

        if abs(f0) < tolerance_metric:
            return t0
        if abs(f1) < tolerance_metric:
            return t1

        t_low, t_high = (t0, t1) if t0 < t1 else (t1, t0)
        f_low, f_high = f0, f1

        for _ in range(max_iterations):
            if abs(f_high - f_low) < 1e-12:
                # Secant denominator too small, fallback to bisection
                t_next = t_low + timedelta(seconds=(t_high - t_low).total_seconds() / 2.0)
            else:
                # Linear interpolation step
                denom = f_high - f_low
                dt_sec = (t_high - t_low).total_seconds()
                t_next = t_low + timedelta(seconds=-f_low * (dt_sec / denom))

            # Ensure t_next stays strictly within bracket
            if not (t_low <= t_next <= t_high):
                t_next = t_low + timedelta(seconds=(t_high - t_low).total_seconds() / 2.0)

            f_next = metric_func(t_next) - target_val

            if (t_high - t_low).total_seconds() <= tolerance_seconds or abs(f_next) <= tolerance_metric:
                return t_next

            # Update brackets maintaining sign change
            if (f_next > 0) == (f_low > 0):
                t_low = t_next
                f_low = f_next
            else:
                t_high = t_next
                f_high = f_next

        return t_high


# ---------------------------------------------------------------------------
# Interval Operations (Intersection, Merging, Filtering)
# ---------------------------------------------------------------------------

class IntervalEngine:
    """High-efficiency 1D interval arithmetic for temporal window processing."""

    @staticmethod
    def merge_overlapping(
        intervals: Sequence[TimeInterval],
        gap_tolerance: timedelta = timedelta(seconds=60),
    ) -> List[TimeInterval]:
        """Merges overlapping or immediately adjacent intervals."""
        if not intervals:
            return []

        sorted_ints = sorted(intervals, key=lambda x: x.start)
        merged: List[TimeInterval] = [sorted_ints[0]]

        for current in sorted_ints[1:]:
            prev = merged[-1]
            if current.start <= prev.end + gap_tolerance:
                # Merge into previous
                merged[-1] = TimeInterval(prev.start, max(prev.end, current.end))
            else:
                merged.append(current)

        return merged

    @staticmethod
    def intersect_pair(
        list_a: Sequence[TimeInterval],
        list_b: Sequence[TimeInterval],
    ) -> List[TimeInterval]:
        """Calculates the intersection of two sorted lists of disjoint intervals."""
        result: List[TimeInterval] = []
        i = 0
        j = 0

        while i < len(list_a) and j < len(list_b):
            start = max(list_a[i].start, list_b[j].start)
            end = min(list_a[i].end, list_b[j].end)

            if start <= end:
                result.append(TimeInterval(start, end))

            if list_a[i].end < list_b[j].end:
                i += 1
            else:
                j += 1

        return result

    @classmethod
    def intersect_all(
        cls,
        interval_sets: Sequence[Sequence[TimeInterval]],
    ) -> List[TimeInterval]:
        """Calculates the simultaneous intersection of multiple interval sets."""
        if not interval_sets:
            return []
        if len(interval_sets) == 1:
            return cls.merge_overlapping(interval_sets[0])

        current = cls.merge_overlapping(interval_sets[0])
        for s in interval_sets[1:]:
            s_merged = cls.merge_overlapping(s)
            current = cls.intersect_pair(current, s_merged)
            if not current:
                break

        return current


# ---------------------------------------------------------------------------
# Core Electional Search Engine
# ---------------------------------------------------------------------------

class ElectionalSearchEngine:
    """
    High-precision astrological election search engine.
    Combines discrete sampling, root-finding boundary refinement,
    and multi-rule interval intersection.
    """

    def __init__(
        self,
        ephemeris_engine: Optional[EphemerisEngine] = None,
        boundary_tolerance_seconds: float = 30.0,
        min_window_duration_minutes: float = 5.0,
        house_system: HouseSystem = HouseSystem.PLACIDUS,
    ):
        self.engine = ephemeris_engine or EphemerisEngine()
        self.boundary_tolerance = boundary_tolerance_seconds
        self.min_duration_minutes = min_window_duration_minutes
        self.house_system = house_system

    def _evaluate_at_dt(
        self,
        dt: datetime,
        coords: GeographicCoordinates,
        rule: ElectionRule,
    ) -> bool:
        chart = self.engine.calculate_chart(dt, coords, self.house_system)
        return rule.evaluate(chart)

    def _metric_at_dt(
        self,
        dt: datetime,
        coords: GeographicCoordinates,
        rule: ElectionRule,
    ) -> float:
        chart = self.engine.calculate_chart(dt, coords, self.house_system)
        if rule.continuous_metric:
            return rule.continuous_metric(chart)
        return 1.0 if rule.evaluate(chart) else -1.0

    def search_rule_intervals(
        self,
        start_datetime: datetime,
        end_datetime: datetime,
        step_minutes: float,
        coords: GeographicCoordinates,
        rule: ElectionRule,
    ) -> List[TimeInterval]:
        """
        Searches continuous windows where a single rule evaluates to True.
        Pinpoints ingress and egress boundaries with root finding.
        """
        step_delta = timedelta(minutes=step_minutes)
        curr_time = start_datetime
        raw_windows: List[TimeInterval] = []

        is_currently_active = self._evaluate_at_dt(curr_time, coords, rule)
        window_start_candidate = curr_time if is_currently_active else None

        while curr_time < end_datetime:
            next_time = min(curr_time + step_delta, end_datetime)
            next_active = self._evaluate_at_dt(next_time, coords, rule)

            # Detect False -> True transition (Ingress)
            if not is_currently_active and next_active:
                if rule.continuous_metric:
                    refined_ingress = RootFinder.secant_continuous_root(
                        metric_func=lambda t: self._metric_at_dt(t, coords, rule),
                        t0=curr_time,
                        t1=next_time,
                        tolerance_seconds=self.boundary_tolerance,
                    )
                else:
                    refined_ingress = RootFinder.bisection_boundary(
                        predicate=lambda t: self._evaluate_at_dt(t, coords, rule),
                        t_left=curr_time,
                        t_right=next_time,
                        tolerance_seconds=self.boundary_tolerance,
                    )
                window_start_candidate = refined_ingress
                is_currently_active = True

            # Detect True -> False transition (Egress)
            elif is_currently_active and not next_active:
                if rule.continuous_metric:
                    refined_egress = RootFinder.secant_continuous_root(
                        metric_func=lambda t: self._metric_at_dt(t, coords, rule),
                        t0=curr_time,
                        t1=next_time,
                        tolerance_seconds=self.boundary_tolerance,
                    )
                else:
                    refined_egress = RootFinder.bisection_boundary(
                        predicate=lambda t: self._evaluate_at_dt(t, coords, rule),
                        t_left=curr_time,
                        t_right=next_time,
                        tolerance_seconds=self.boundary_tolerance,
                    )
                if window_start_candidate and refined_egress > window_start_candidate:
                    raw_windows.append(TimeInterval(window_start_candidate, refined_egress))
                window_start_candidate = None
                is_currently_active = False

            curr_time = next_time

        # Handle condition active through the end of the search range
        if is_currently_active and window_start_candidate and end_datetime > window_start_candidate:
            raw_windows.append(TimeInterval(window_start_candidate, end_datetime))

        return IntervalEngine.merge_overlapping(raw_windows)

    def search_electional_windows(
        self,
        start_datetime: datetime,
        end_datetime: datetime,
        step_minutes: float,
        coordinates: GeographicCoordinates,
        ruleset: Sequence[ElectionRule],
        composite_mode: str = "ALL",  # "ALL" (simultaneous intersection) or "ANY" (union)
    ) -> List[ElectionWindow]:
        """
        Main entry point for election search.
        Executes discrete sampling, boundary root finding, and interval combination.
        Returns array of ElectionWindows with calculated chart states.
        """
        if start_datetime >= end_datetime:
            raise ValueError(f"start_datetime ({start_datetime}) must be before end_datetime ({end_datetime})")
        if step_minutes <= 0:
            raise ValueError(f"step_minutes must be > 0, got {step_minutes}")
        if not ruleset:
            raise ValueError("ruleset array cannot be empty")

        # 1. Search continuous intervals for each individual rule
        rule_intervals: List[List[TimeInterval]] = []
        for rule in ruleset:
            intervals = self.search_rule_intervals(
                start_datetime=start_datetime,
                end_datetime=end_datetime,
                step_minutes=step_minutes,
                coords=coordinates,
                rule=rule,
            )
            rule_intervals.append(intervals)

        # 2. Combine overlapping/simultaneous condition windows
        if composite_mode.upper() == "ALL":
            combined_intervals = IntervalEngine.intersect_all(rule_intervals)
        elif composite_mode.upper() == "ANY":
            all_flat = [interval for sublist in rule_intervals for interval in sublist]
            combined_intervals = IntervalEngine.merge_overlapping(all_flat)
        else:
            raise ValueError(f"Unsupported composite_mode: {composite_mode}. Use 'ALL' or 'ANY'.")

        # 3. Filter by minimum duration
        valid_intervals = [
            ti for ti in combined_intervals
            if ti.duration_minutes >= self.min_duration_minutes
        ]

        # 4. Synthesize ElectionWindow objects with rich planetary calculations
        election_windows: List[ElectionWindow] = []
        rule_names = [r.name for r in ruleset]

        for interval in valid_intervals:
            mid_time = interval.start + timedelta(seconds=interval.duration.total_seconds() / 2.0)

            start_chart = self.engine.calculate_chart(interval.start, coordinates, self.house_system)
            mid_chart = self.engine.calculate_chart(mid_time, coordinates, self.house_system)
            end_chart = self.engine.calculate_chart(interval.end, coordinates, self.house_system)

            election_windows.append(
                ElectionWindow(
                    interval=interval,
                    start_chart=start_chart,
                    mid_chart=mid_chart,
                    end_chart=end_chart,
                    satisfied_rules=rule_names,
                    quality_score=1.0,
                )
            )

        return election_windows


# ---------------------------------------------------------------------------
# High-Level Electional Rules Factory
# ---------------------------------------------------------------------------

class ElectionRules:
    """Pre-built factory for classical and modern electional rules."""

    @staticmethod
    def planet_in_houses(
        body: CelestialBody,
        houses: Set[int],
        name: Optional[str] = None,
    ) -> ElectionRule:
        rule_name = name or f"{body.display_name} in Houses {sorted(houses)}"

        def predicate(chart: ChartState) -> bool:
            return chart.planets[body].house in houses

        return ElectionRule(
            name=rule_name,
            predicate=predicate,
            description=f"Ensures {body.display_name} occupies house(s) {sorted(houses)}.",
        )

    @staticmethod
    def planet_in_signs(
        body: CelestialBody,
        signs: Set[ZodiacSign],
        name: Optional[str] = None,
    ) -> ElectionRule:
        rule_name = name or f"{body.display_name} in {', '.join(s.sign_name for s in signs)}"

        def predicate(chart: ChartState) -> bool:
            return chart.planets[body].sign in signs

        def continuous_metric(chart: ChartState) -> float:
            lon = chart.planets[body].longitude
            current_sign = chart.planets[body].sign
            # Distance from ingress/egress within target signs
            if current_sign in signs:
                dist_to_egress = 30.0 - chart.planets[body].sign_degree
                dist_from_ingress = chart.planets[body].sign_degree
                return min(dist_to_egress, dist_from_ingress)
            return -1.0

        return ElectionRule(
            name=rule_name,
            predicate=predicate,
            continuous_metric=continuous_metric,
            description=f"Ensures {body.display_name} is situated in specified zodiac sign(s).",
        )

    @staticmethod
    def ascendant_in_signs(
        signs: Set[ZodiacSign],
        name: Optional[str] = None,
    ) -> ElectionRule:
        rule_name = name or f"Ascendant in {', '.join(s.sign_name for s in signs)}"

        def predicate(chart: ChartState) -> bool:
            return chart.ascendant_sign in signs

        return ElectionRule(
            name=rule_name,
            predicate=predicate,
            description="Ensures rising sign (Ascendant) is one of the designated zodiac signs.",
        )

    @staticmethod
    def planet_direct(
        body: CelestialBody,
        name: Optional[str] = None,
    ) -> ElectionRule:
        rule_name = name or f"{body.display_name} Direct in Motion"

        def predicate(chart: ChartState) -> bool:
            return not chart.planets[body].is_retrograde

        def continuous_metric(chart: ChartState) -> float:
            return chart.planets[body].speed_longitude

        return ElectionRule(
            name=rule_name,
            predicate=predicate,
            continuous_metric=continuous_metric,
            description=f"Ensures {body.display_name} is direct (not retrograde).",
        )

    @staticmethod
    def planet_aspect(
        body1: CelestialBody,
        body2: CelestialBody,
        aspect: MajorAspect,
        orb_degrees: float,
        applying_only: bool = False,
        name: Optional[str] = None,
    ) -> ElectionRule:
        rule_name = (
            name or f"{body1.display_name} {aspect.name.lower()} {body2.display_name} (orb <= {orb_degrees}°)"
        )

        def predicate(chart: ChartState) -> bool:
            sep = chart.get_aspect_separation(body1, body2)
            within_orb = abs(sep - aspect.value) <= orb_degrees
            if not within_orb:
                return False
            if applying_only:
                return chart.is_aspect_applying(body1, body2, aspect.value)
            return True

        def continuous_metric(chart: ChartState) -> float:
            sep = chart.get_aspect_separation(body1, body2)
            # Positive when within orb, zero at boundary
            return orb_degrees - abs(sep - aspect.value)

        return ElectionRule(
            name=rule_name,
            predicate=predicate,
            continuous_metric=continuous_metric,
            description=f"{body1.display_name} within {orb_degrees}° of {aspect.name} with {body2.display_name}.",
        )

    @staticmethod
    def waxing_moon(name: str = "Waxing Moon") -> ElectionRule:
        """Moon ahead of the Sun by 0° to 180° in zodiacal longitude."""
        def predicate(chart: ChartState) -> bool:
            moon_lon = chart.planets[CelestialBody.MOON].longitude
            sun_lon = chart.planets[CelestialBody.SUN].longitude
            elongation = (moon_lon - sun_lon) % 360.0
            return 0.0 < elongation < 180.0

        def continuous_metric(chart: ChartState) -> float:
            moon_lon = chart.planets[CelestialBody.MOON].longitude
            sun_lon = chart.planets[CelestialBody.SUN].longitude
            elongation = (moon_lon - sun_lon) % 360.0
            # Root at 0° (New Moon) and 180° (Full Moon)
            return min(elongation, 180.0 - elongation)

        return ElectionRule(
            name=name,
            predicate=predicate,
            continuous_metric=continuous_metric,
            description="Ensures the Moon is increasing in light (Waxing).",
        )

    @staticmethod
    def angular_benefic(
        body: CelestialBody = CelestialBody.JUPITER,
        name: Optional[str] = None,
    ) -> ElectionRule:
        """Ensures Jupiter or Venus is situated in an angular house (1st, 10th, 7th, or 4th)."""
        rule_name = name or f"{body.display_name} Angular (H1, H4, H7, H10)"
        return ElectionRules.planet_in_houses(body, {1, 4, 7, 10}, name=rule_name)

    @staticmethod
    def ascendant_lord_well_placed(name: str = "Ascendant Ruler Angular/Succedent & Direct") -> ElectionRule:
        """
        Classical electional canon: Ruler of the Ascendant must not be in cadent houses (3, 6, 8, 12)
        and must be direct in motion.
        """
        def predicate(chart: ChartState) -> bool:
            asc_sign = chart.ascendant_sign
            ruler = asc_sign.traditional_ruler
            ruler_state = chart.planets[ruler]
            if ruler_state.is_retrograde:
                return False
            # Malefic/cadent exclusion: houses 6, 8, 12
            return ruler_state.house not in {6, 8, 12}

        return ElectionRule(
            name=name,
            predicate=predicate,
            description="Lord of the 1st house is free from retrograde motion and not in dusthana/cadent houses.",
        )


# ---------------------------------------------------------------------------
# Public Module API / Convenience Function
# ---------------------------------------------------------------------------

def search_election_times(
    start_datetime: datetime,
    end_datetime: datetime,
    step_minutes: float,
    geographic_coordinates: Union[Tuple[float, float], GeographicCoordinates],
    ruleset: Sequence[ElectionRule],
    house_system: HouseSystem = HouseSystem.PLACIDUS,
    boundary_tolerance_seconds: float = 30.0,
    min_window_duration_minutes: float = 5.0,
    ephe_path: Optional[str] = None,
    composite_mode: str = "ALL",
) -> List[ElectionWindow]:
    """
    High-precision entry function for electional astrological search.

    Parameters:
        start_datetime: Search start timestamp (UTC).
        end_datetime: Search end timestamp (UTC).
        step_minutes: Step size for initial discrete sampling grid (e.g. 5 minutes).
        geographic_coordinates: Either (latitude, longitude) tuple or GeographicCoordinates object.
        ruleset: Array of ElectionRule objects.
        house_system: HouseSystem enum (default: Placidus).
        boundary_tolerance_seconds: Bisection/Secant convergence threshold in seconds.
        min_window_duration_minutes: Discard election windows shorter than this.
        ephe_path: Optional custom directory path for Swiss Ephemeris data files.
        composite_mode: 'ALL' to require all rules simultaneously, or 'ANY' for union.

    Returns:
        List of ElectionWindow objects with start/end boundaries and calculated planetary states.
    """
    if isinstance(geographic_coordinates, tuple):
        lat, lon = geographic_coordinates
        coords = GeographicCoordinates(latitude=lat, longitude=lon)
    else:
        coords = geographic_coordinates

    ephe_engine = EphemerisEngine(ephe_path=ephe_path)
    searcher = ElectionalSearchEngine(
        ephemeris_engine=ephe_engine,
        boundary_tolerance_seconds=boundary_tolerance_seconds,
        min_window_duration_minutes=min_window_duration_minutes,
        house_system=house_system,
    )

    return searcher.search_electional_windows(
        start_datetime=start_datetime,
        end_datetime=end_datetime,
        step_minutes=step_minutes,
        coordinates=coords,
        ruleset=ruleset,
        composite_mode=composite_mode,
    )


# ---------------------------------------------------------------------------
# Self-Test / Demonstration Entry Point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    print("=" * 70)
    print("Electional Astrology Engine: High-Precision Search Verification")
    print("=" * 70)

    # 1. Setup sample input parameters: London coordinates
    london = GeographicCoordinates(latitude=51.5074, longitude=-0.1278)
    search_start = datetime(2026, 9, 15, 0, 0, 0, tzinfo=timezone.utc)
    search_end = datetime(2026, 9, 18, 0, 0, 0, tzinfo=timezone.utc)
    sampling_step = 5.0  # 5-minute sampling interval

    # 2. Construct sample electional ruleset:
    #    Rule A: Ascendant in Taurus, Cancer, or Libra (Venusian/Lunar benefics)
    #    Rule B: Jupiter in an angular house (1, 4, 7, 10)
    #    Rule C: Moon is Waxing
    rules = [
        ElectionRules.ascendant_in_signs(
            {ZodiacSign.TAURUS, ZodiacSign.CANCER, ZodiacSign.LIBRA},
            name="Ascendant in Taurus, Cancer, or Libra"
        ),
        ElectionRules.angular_benefic(
            CelestialBody.JUPITER,
            name="Jupiter Angular"
        ),
        ElectionRules.waxing_moon("Waxing Moon Phase"),
    ]

    print(f"Search Window: {search_start.isoformat()} to {search_end.isoformat()}")
    print(f"Coordinates:   Lat {london.latitude}, Lon {london.longitude}")
    print(f"Sampling Step: {sampling_step} minutes")
    print(f"Rules Applied: {len(rules)}")
    for i, r in enumerate(rules, 1):
        print(f"  {i}. {r.name}")

    # 3. Execute electional search
    windows = search_election_times(
        start_datetime=search_start,
        end_datetime=search_end,
        step_minutes=sampling_step,
        geographic_coordinates=london,
        ruleset=rules,
        house_system=HouseSystem.PLACIDUS,
        boundary_tolerance_seconds=10.0,
        min_window_duration_minutes=5.0,
        composite_mode="ALL",
    )

    print(f"\nSearch complete: Found {len(windows)} electional window(s).\n")

    for idx, win in enumerate(windows, 1):
        print(f"--- Window #{idx} ---")
        print(win.summary())
        mid = win.mid_chart
        print("Planetary Snapshot at Midpoint:")
        for body in [CelestialBody.SUN, CelestialBody.MOON, CelestialBody.VENUS, CelestialBody.JUPITER]:
            p = mid.planets[body]
            print(f"   {p.formatted_position()}")
        print("-" * 50)
