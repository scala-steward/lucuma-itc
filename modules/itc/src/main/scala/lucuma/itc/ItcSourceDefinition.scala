// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.itc

import io.circe.Encoder
import io.circe.Json
import io.circe.syntax._
import lucuma.core.enums._
import lucuma.core.math.Angle
import lucuma.core.math.Redshift
import lucuma.core.model.SourceProfile
import lucuma.core.model.SpectralDefinition
import lucuma.core.model.UnnormalizedSED
import lucuma.itc.search.TargetProfile
import lucuma.itc.search.syntax.sed._

final case class ItcSourceDefinition(
  profile:  SourceProfile,
  normBand: Band,
  redshift: Redshift
)

object ItcSourceDefinition {

  def fromTargetProfile(p: TargetProfile): ItcSourceDefinition =
    ItcSourceDefinition(
      p.sourceProfile,
      p.band,
      p.redshift
    )

  implicit val sourceProfileEncoder: Encoder[SourceProfile] =
    new Encoder[SourceProfile] {
      import SourceProfile._
      def apply(a: SourceProfile): Json =
        a match {
          case _: Point    =>
            Json.obj("PointSource" -> Json.obj())
          case _: Uniform  => Json.obj("UniformSource" -> Json.obj())
          case g: Gaussian =>
            Json.obj(
              "GaussianSource" -> Json.obj(
                "fwhm" -> Angle.signedDecimalArcseconds.get(g.fwhm).asJson
              )
            )
        }
    }

  implicit val spectralDistributionEncoder: Encoder[UnnormalizedSED] =
    new Encoder[UnnormalizedSED] {
      import UnnormalizedSED._
      def apply(a: UnnormalizedSED): Json =
        a match {
          case BlackBody(t)       =>
            Json.obj(
              "BlackBody" -> Json.obj(
                "temperature" -> Json.fromDoubleOrNull(t.value.value.toDouble)
              )
            )
          case PowerLaw(i)        =>
            Json.obj("PowerLaw" -> Json.obj("index" -> Json.fromDoubleOrNull(i.toDouble)))
          case StellarLibrary(s)  =>
            Json.obj("Library" -> Json.obj("LibraryStar" -> Json.fromString(s.ocs2Tag)))
          case s: CoolStarModel   =>
            Json.obj("Library" -> Json.obj("LibraryStar" -> Json.fromString(s.ocs2Tag)))
          case PlanetaryNebula(s) =>
            Json.obj("Library" -> Json.obj("LibraryStar" -> Json.fromString(s.ocs2Tag)))
          case Galaxy(s)          =>
            Json.obj("Library" -> Json.obj("LibraryNonStar" -> Json.fromString(s.ocs2Tag)))
          case Planet(s)          =>
            Json.obj("Library" -> Json.obj("LibraryNonStar" -> Json.fromString(s.ocs2Tag)))
          case HIIRegion(s)       =>
            Json.obj("Library" -> Json.obj("LibraryNonStar" -> Json.fromString(s.ocs2Tag)))
          case Quasar(s)          =>
            Json.obj("Library" -> Json.obj("LibraryNonStar" -> Json.fromString(s.ocs2Tag)))
          case _                  => // TODO UserDefined
            Json.obj("Library" -> Json.Null)
        }
    }

  implicit val bandEncoder: Encoder[Band] =
    Encoder[String].contramap(_.shortName)

  implicit val redshiftEncoder: Encoder[Redshift] =
    Encoder.forProduct1("z")(_.z)

  implicit val encoder: Encoder[ItcSourceDefinition] =
    new Encoder[ItcSourceDefinition] {
      def apply(s: ItcSourceDefinition): Json = {
        val source = s.profile match {
          case _: SourceProfile.Point    =>
            Json.obj("PointSource" -> Json.obj())
          case _: SourceProfile.Uniform  => Json.obj("UniformSource" -> Json.obj())
          case g: SourceProfile.Gaussian =>
            Json.obj(
              "GaussianSource" -> Json.obj(
                "fwhm" -> Angle.signedDecimalArcseconds.get(g.fwhm).asJson
              )
            )
        }

        val units: Json = s.profile match {
          case SourceProfile.Point(SpectralDefinition.BandNormalized(_, brightnesses))
              if brightnesses.contains(s.normBand) =>
            brightnesses.get(s.normBand).map(_.units.serialized) match {
              case Some("VEGA_MAGNITUDE")                  => Json.obj("MagnitudeSystem" -> Json.fromString("Vega"))
              case Some("AB_MAGNITUDE")                    => Json.obj("MagnitudeSystem" -> Json.fromString("AB"))
              case Some("JANSKY")                          => Json.obj("MagnitudeSystem" -> Json.fromString("Jy"))
              case Some("W_PER_M_SQUARED_PER_UM")          =>
                Json.obj("MagnitudeSystem" -> Json.fromString("W/m²/µm"))
              case Some("ERG_PER_S_PER_CM_SQUARED_PER_A")  =>
                Json.obj("MagnitudeSystem" -> Json.fromString("erg/s/cm²/Å"))
              case Some("ERG_PER_S_PER_CM_SQUARED_PER_HZ") =>
                Json.obj("MagnitudeSystem" -> Json.fromString("erg/s/cm²/Hz"))
              case _                                       =>
                Json.Null
            }
          // FIXME Support emision lines
          // case SourceProfile.Point(SpectralDefinition.EmissionLines(_, brightnesses)) =>
          //   Json.Null
          //     if brightnesses.contains(s.normBand) =>
          //   brightnesses.get(s.normBand).map(_.units.serialized) match {
          //     case Some("VEGA_MAGNITUDE")                  => Json.obj("MagnitudeSystem" -> Json.fromString("Vega"))
          //     case Some("AB_MAGNITUDE")                    => Json.obj("MagnitudeSystem" -> Json.fromString("AB"))
          //     case Some("JANSKY")                          => Json.obj("MagnitudeSystem" -> Json.fromString("Jy"))
          //     case Some("W_PER_M_SQUARED_PER_UM")          =>
          //       Json.obj("MagnitudeSystem" -> Json.fromString("W/m²/µm"))
          //     case Some("ERG_PER_S_PER_CM_SQUARED_PER_A")  =>
          //       Json.obj("MagnitudeSystem" -> Json.fromString("erg/s/cm²/Å"))
          //     case Some("ERG_PER_S_PER_CM_SQUARED_PER_HZ") =>
          //       Json.obj("MagnitudeSystem" -> Json.fromString("erg/s/cm²/Hz"))
          //     case _                                       =>
          //       Json.Null
          //   }
          case SourceProfile.Uniform(SpectralDefinition.BandNormalized(_, brightnesses))
              if brightnesses.contains(s.normBand) =>
            brightnesses.get(s.normBand).map(_.units.serialized) match {
              case Some("VEGA_MAG_PER_ARCSEC_SQUARED")                        =>
                Json.obj("SurfaceBrightness" -> Json.fromString("Vega mag/arcsec²"))
              case Some("AB_MAG_PER_ARCSEC_SQUARED")                          =>
                Json.obj("SurfaceBrightness" -> Json.fromString("AB mag/arcsec²"))
              case Some("JY_PER_ARCSEC_SQUARED")                              =>
                Json.obj("SurfaceBrightness" -> Json.fromString("Jy/arcsec²"))
              case Some("W_PER_M_SQUARED_PER_UM_PER_ARCSEC_SQUARED")          =>
                Json.obj("SurfaceBrightness" -> Json.fromString("W/m²/µm/arcsec²"))
              case Some("ERG_PER_S_PER_CM_SQUARED_PER_A_PER_ARCSEC_SQUARED")  =>
                Json.obj("SurfaceBrightness" -> Json.fromString("erg/s/cm²/Å/arcsec²"))
              case Some("ERG_PER_S_PER_CM_SQUARED_PER_HZ_PER_ARCSEC_SQUARED") =>
                Json.obj("SurfaceBrightness" -> Json.fromString("erg/s/cm²/Hz/arcsec²"))
              case _                                                          =>
                Json.Null
            }
          case SourceProfile.Gaussian(_, SpectralDefinition.BandNormalized(_, brightnesses))
              if brightnesses.contains(s.normBand) =>
            brightnesses.get(s.normBand).map(_.units.serialized) match {
              case Some("VEGA_MAGNITUDE")                  => Json.obj("MagnitudeSystem" -> Json.fromString("Vega"))
              case Some("AB_MAGNITUDE")                    => Json.obj("MagnitudeSystem" -> Json.fromString("AB"))
              case Some("JANSKY")                          => Json.obj("MagnitudeSystem" -> Json.fromString("Jy"))
              case Some("W_PER_M_SQUARED_PER_UM")          =>
                Json.obj("MagnitudeSystem" -> Json.fromString("W/m²/µm"))
              case Some("ERG_PER_S_PER_CM_SQUARED_PER_A")  =>
                Json.obj("MagnitudeSystem" -> Json.fromString("erg/s/cm²/Å"))
              case Some("ERG_PER_S_PER_CM_SQUARED_PER_HZ") =>
                Json.obj("MagnitudeSystem" -> Json.fromString("erg/s/cm²/Hz"))
              case _                                       =>
                Json.Null
            }

          // FIXME Support emision lines
          case _ => Json.Null
        }

        val value: Json = s.profile match {
          case SourceProfile.Point(SpectralDefinition.BandNormalized(_, brightnesses))
              if brightnesses.contains(s.normBand) =>
            brightnesses
              .get(s.normBand)
              .map(_.value.toDouble)
              .flatMap(Json.fromDouble)
              .getOrElse(Json.Null)
          case SourceProfile.Uniform(SpectralDefinition.BandNormalized(_, brightnesses))
              if brightnesses.contains(s.normBand) =>
            brightnesses
              .get(s.normBand)
              .map(_.value.toDouble)
              .flatMap(Json.fromDouble)
              .getOrElse(Json.Null)
          case SourceProfile.Gaussian(_, SpectralDefinition.BandNormalized(_, brightnesses))
              if brightnesses.contains(s.normBand) =>
            brightnesses
              .get(s.normBand)
              .map(_.value.toDouble)
              .flatMap(Json.fromDouble)
              .getOrElse(Json.Null)
          // FIXME: Handle emission line
          case _ => Json.Null
        }

        val distribution = s.profile match {
          case SourceProfile.Point(SpectralDefinition.BandNormalized(sed, _))       =>
            sed.asJson
          // FIXME support emmision lines
          // case SourceProfile.Point(SpectralDefinition.EmissionLines(sed, _))        =>
          //   Json.Null
          case SourceProfile.Uniform(SpectralDefinition.BandNormalized(sed, _))     =>
            sed.asJson
          case SourceProfile.Gaussian(_, SpectralDefinition.BandNormalized(sed, _)) =>
            sed.asJson
          // FIXME: Handle emission line
          case _                                                                    => Json.Null
        }

        Json.obj("profile"      -> source,
                 "normBand"     -> s.normBand.asJson,
                 "norm"         -> value,
                 "redshift"     -> s.redshift.asJson,
                 "units"        -> units,
                 "distribution" -> distribution
        )
      }
    }

}
